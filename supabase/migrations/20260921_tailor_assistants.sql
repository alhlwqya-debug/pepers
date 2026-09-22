-- Pepers: tailor assistants, historical piece rates, daily status/allowances, withdrawals.
-- The live Supabase project is migrated by this same SQL.

create table if not exists public.assistants (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  legacy_id bigint not null,
  record_key text not null,
  source_device_id text not null,
  shop_legacy_id bigint not null,
  shop_record_key text not null,
  worker_legacy_id bigint,
  worker_record_key text,
  name text not null,
  task text not null default '',
  phone text not null default '',
  link_code text not null default '',
  default_rate integer not null default 0 check(default_rate >= 0),
  start_date text not null,
  end_date text,
  active boolean not null default true,
  notes text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, record_key)
);
create table if not exists public.assistant_piece_rates (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  legacy_id bigint not null,
  record_key text not null,
  source_device_id text not null,
  assistant_legacy_id bigint not null,
  assistant_record_key text not null,
  piece_legacy_id bigint not null,
  piece_record_key text not null,
  rate integer not null default 0 check(rate >= 0),
  effective_from text not null,
  effective_to text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, record_key)
);
create table if not exists public.assistant_daily_records (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  legacy_id bigint not null,
  record_key text not null,
  source_device_id text not null,
  assistant_legacy_id bigint not null,
  assistant_record_key text not null,
  day_legacy_id bigint not null,
  day_record_key text not null,
  status text not null default 'WORKED' check(status in ('WORKED','ABSENT','NO_WORK')),
  expense integer not null default 0 check(expense >= 0),
  expense_note text not null default '',
  notes text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, record_key)
);
create table if not exists public.assistant_withdrawals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  legacy_id bigint not null,
  record_key text not null,
  source_device_id text not null,
  assistant_legacy_id bigint not null,
  assistant_record_key text not null,
  date_value text not null,
  amount integer not null default 0 check(amount > 0),
  note text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, record_key)
);

do $$
declare t text;
begin
  foreach t in array array['assistants','assistant_piece_rates','assistant_daily_records','assistant_withdrawals'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on table public.%I from anon, authenticated', t);
    execute format('grant select, insert, update, delete on table public.%I to authenticated', t);
    execute format('drop policy if exists %I on public.%I', t || '_owner_all', t);
    execute format(
      'create policy %I on public.%I for all to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id)',
      t || '_owner_all', t
    );
    execute format('create index if not exists %I on public.%I(user_id)', t || '_user_idx', t);
    execute format('create index if not exists %I on public.%I(record_key)', t || '_record_idx', t);
  end loop;
end $$;

create index if not exists assistants_shop_idx on public.assistants(shop_legacy_id);
create index if not exists assistants_worker_idx on public.assistants(worker_legacy_id);
create index if not exists assistant_rates_assistant_idx on public.assistant_piece_rates(assistant_legacy_id);
create index if not exists assistant_rates_piece_idx on public.assistant_piece_rates(piece_legacy_id);
create index if not exists assistant_daily_assistant_idx on public.assistant_daily_records(assistant_legacy_id);
create index if not exists assistant_daily_day_idx on public.assistant_daily_records(day_legacy_id);
create index if not exists assistant_withdrawals_assistant_idx on public.assistant_withdrawals(assistant_legacy_id);

create trigger set_assistants_updated_at before update on public.assistants for each row execute function public.set_updated_at();
create trigger set_assistant_piece_rates_updated_at before update on public.assistant_piece_rates for each row execute function public.set_updated_at();
create trigger set_assistant_daily_updated_at before update on public.assistant_daily_records for each row execute function public.set_updated_at();
create trigger set_assistant_withdrawals_updated_at before update on public.assistant_withdrawals for each row execute function public.set_updated_at();

create trigger reject_stale_assistants before update on public.assistants for each row execute function public.reject_stale_sync_update();
create trigger reject_stale_assistant_rates before update on public.assistant_piece_rates for each row execute function public.reject_stale_sync_update();
create trigger reject_stale_assistant_daily before update on public.assistant_daily_records for each row execute function public.reject_stale_sync_update();
create trigger reject_stale_assistant_withdrawals before update on public.assistant_withdrawals for each row execute function public.reject_stale_sync_update();

-- Account-linking fields added for independent assistant accounts.
alter table public.assistants add column if not exists phone text not null default '';
alter table public.assistants add column if not exists link_code text not null default '';
alter table public.assistants add column if not exists default_rate integer not null default 0 check(default_rate >= 0);
create unique index if not exists assistants_link_code_idx on public.assistants(link_code) where link_code <> '';


-- Secure account linking and shared daily workspace.
create table if not exists public.assistant_link_requests (
  id uuid primary key default gen_random_uuid(),
  assistant_id uuid not null references public.assistants(id) on delete cascade,
  assistant_user_id uuid not null references auth.users(id) on delete cascade,
  tailor_user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'PENDING' check(status in ('PENDING','APPROVED','REJECTED','REVOKED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.assistant_link_requests enable row level security;
revoke all on public.assistant_link_requests from anon, authenticated;
grant select, insert, update on public.assistant_link_requests to authenticated;
drop policy if exists assistant_link_request_parties on public.assistant_link_requests;
create policy assistant_link_request_parties on public.assistant_link_requests for all to authenticated
using ((select auth.uid()) = assistant_user_id or (select auth.uid()) = tailor_user_id)
with check ((select auth.uid()) = assistant_user_id or (select auth.uid()) = tailor_user_id);
create index if not exists assistant_link_requests_tailor_idx on public.assistant_link_requests(tailor_user_id,status);
create index if not exists assistant_link_requests_assistant_idx on public.assistant_link_requests(assistant_user_id,status);

alter table public.assistant_daily_records add column if not exists reported_quantity integer not null default 0 check(reported_quantity >= 0);
alter table public.assistant_daily_records add column if not exists entered_by text not null default 'TAILOR' check(entered_by in ('TAILOR','ASSISTANT'));
alter table public.assistant_daily_records add column if not exists approval_status text not null default 'APPROVED' check(approval_status in ('PENDING','APPROVED','REJECTED'));

create or replace function public.request_assistant_link(p_link_code text)
returns jsonb language plpgsql security definer set search_path=public
as $$
declare a public.assistants%rowtype; req jsonb;
begin
  select * into a from public.assistants where upper(link_code)=upper(trim(p_link_code)) and active=true limit 1;
  if a.id is null then raise exception 'ASSISTANT_LINK_CODE_NOT_FOUND'; end if;
  if a.user_id = auth.uid() then raise exception 'ASSISTANT_SELF_LINK'; end if;
  insert into public.assistant_link_requests(assistant_id,assistant_user_id,tailor_user_id,status)
  values(a.id,auth.uid(),a.user_id,'PENDING');
  return jsonb_build_object('assistant_id',a.id,'name',a.name,'task',a.task,'status','PENDING');
end $$;

create or replace function public.approve_assistant_link(p_request_id uuid, p_approve boolean)
returns jsonb language plpgsql security definer set search_path=public
as $$
declare r public.assistant_link_requests%rowtype;
begin
  select * into r from public.assistant_link_requests where id=p_request_id and tailor_user_id=auth.uid() for update;
  if r.id is null then raise exception 'LINK_REQUEST_NOT_FOUND'; end if;
  update public.assistant_link_requests set status=case when p_approve then 'APPROVED' else 'REJECTED' end,updated_at=now() where id=r.id;
  return jsonb_build_object('request_id',r.id,'status',case when p_approve then 'APPROVED' else 'REJECTED' end);
end $$;

create or replace function public.my_assistant_link()
returns jsonb language sql security definer set search_path=public
as $$
select coalesce(jsonb_agg(jsonb_build_object('request_id',r.id,'assistant_id',r.assistant_id,'name',a.name,'task',a.task,'phone',a.phone,'link_code',a.link_code,'rate',a.default_rate,'status',r.status)), '[]'::jsonb)
from public.assistant_link_requests r join public.assistants a on a.id=r.assistant_id
where r.assistant_user_id=auth.uid() and r.status='APPROVED';
$$;

create or replace function public.pending_assistant_links()
returns jsonb language sql security definer set search_path=public
as $$
select coalesce(jsonb_agg(jsonb_build_object('request_id',r.id,'assistant_id',r.assistant_id,'name',a.name,'task',a.task,'phone',a.phone,'status',r.status)), '[]'::jsonb)
from public.assistant_link_requests r join public.assistants a on a.id=r.assistant_id
where r.tailor_user_id=auth.uid() and r.status='PENDING';
$$;

grant execute on function public.request_assistant_link(text) to authenticated;
grant execute on function public.approve_assistant_link(uuid,boolean) to authenticated;
grant execute on function public.my_assistant_link() to authenticated;
grant execute on function public.pending_assistant_links() to authenticated;


create unique index if not exists assistant_one_pending_link_idx
on public.assistant_link_requests(assistant_id, assistant_user_id)
where status='PENDING';

create or replace function public.upsert_my_assistant_daily(
  p_date text, p_quantity integer, p_expense integer, p_note text default ''
) returns jsonb language plpgsql security definer set search_path=public
as $f$
declare a public.assistants%rowtype; d public.assistant_daily_records%rowtype;
begin
  select a.* into a from public.assistants a
  join public.assistant_link_requests r on r.assistant_id=a.id
  where r.assistant_user_id=auth.uid() and r.status='APPROVED' and a.active=true
    and p_date >= a.start_date and (a.end_date is null or p_date <= a.end_date)
  limit 1;
  if a.id is null then raise exception 'ASSISTANT_NOT_LINKED'; end if;
  insert into public.assistant_daily_records(
    user_id,legacy_id,record_key,source_device_id,assistant_legacy_id,assistant_record_key,
    day_legacy_id,day_record_key,status,expense,expense_note,notes,reported_quantity,entered_by,approval_status
  )
  values(a.user_id,extract(epoch from now())::bigint,
    'shared:'||a.id::text||':'||p_date,'assistant-account:'||auth.uid()::text,
    a.legacy_id,a.record_key,0,'shared-day:'||p_date,'WORKED',greatest(p_expense,0),coalesce(p_note,''),coalesce(p_note,''),
    greatest(p_quantity,0),'ASSISTANT','PENDING')
  on conflict (user_id,record_key) do update set
    expense=excluded.expense,expense_note=excluded.expense_note,notes=excluded.notes,
    reported_quantity=excluded.reported_quantity,entered_by='ASSISTANT',approval_status='PENDING',updated_at=now()
  returning * into d;
  return jsonb_build_object('status','PENDING','date',p_date,'quantity',d.reported_quantity,'expense',d.expense,'assistant_id',a.id);
end $f$;
grant execute on function public.upsert_my_assistant_daily(text,integer,integer,text) to authenticated;


create or replace function public.pending_assistant_daily()
returns jsonb language sql security definer set search_path=public
as $f$
select coalesce(jsonb_agg(jsonb_build_object(
 'id',d.id,'assistant_id',a.id,'name',a.name,'date',split_part(d.record_key,':',3),
 'quantity',d.reported_quantity,'expense',d.expense,'note',d.notes,'status',d.approval_status
)), '[]'::jsonb)
from public.assistant_daily_records d
join public.assistants a on a.record_key=d.assistant_record_key
where d.user_id=auth.uid() and d.approval_status='PENDING';
$f$;

create or replace function public.approve_assistant_daily(p_record_id uuid,p_approve boolean,p_quantity integer default null,p_expense integer default null)
returns jsonb language plpgsql security definer set search_path=public
as $f$
declare d public.assistant_daily_records%rowtype;
begin
 select * into d from public.assistant_daily_records where id=p_record_id and user_id=auth.uid() for update;
 if d.id is null then raise exception 'DAILY_RECORD_NOT_FOUND'; end if;
 update public.assistant_daily_records
 set approval_status=case when p_approve then 'APPROVED' else 'REJECTED' end,
     reported_quantity=case when p_approve and p_quantity is not null then greatest(p_quantity,0) else reported_quantity end,
     expense=case when p_approve and p_expense is not null then greatest(p_expense,0) else expense end,
     updated_at=now()
 where id=d.id;
 return jsonb_build_object('id',d.id,'status',case when p_approve then 'APPROVED' else 'REJECTED' end);
end $f$;
grant execute on function public.pending_assistant_daily() to authenticated;
grant execute on function public.approve_assistant_daily(uuid,boolean,integer,integer) to authenticated;


create or replace function public.my_assistant_daily()
returns jsonb language sql security definer set search_path=public
as $f$
select coalesce(jsonb_agg(jsonb_build_object(
 'id',d.id,'date',coalesce(nullif(split_part(d.record_key,':',3),''),''),'quantity',d.reported_quantity,
 'expense',d.expense,'note',d.notes,'status',d.status,'approval_status',d.approval_status
) order by d.updated_at desc), '[]'::jsonb)
from public.assistant_daily_records d
join public.assistants a on a.record_key=d.assistant_record_key
join public.assistant_link_requests r on r.assistant_id=a.id
where r.assistant_user_id=auth.uid() and r.status='APPROVED' and d.approval_status='APPROVED';
$f$;
grant execute on function public.my_assistant_daily() to authenticated;
