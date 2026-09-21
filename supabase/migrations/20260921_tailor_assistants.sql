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
