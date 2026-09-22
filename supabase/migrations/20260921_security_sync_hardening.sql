-- Pepers database hardening: RLS performance + function execution safety.
-- Keeps the existing data model intact.

do $$
declare
  t text;
begin
  foreach t in array array[
    'user_profiles','shops','workers','months','pieces','days',
    'entries','individual_entries','individual_entry_items'
  ] loop
    execute format('drop policy if exists %I on public.%I', t || '_owner_all', t);
    execute format(
      'create policy %I on public.%I for all to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id)',
      t || '_owner_all', t
    );
  end loop;
end $$;

alter function public.set_pepers_profile_updated_at()
  set search_path = public;

revoke execute on function public.rls_auto_enable() from public, anon, authenticated;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  if new.updated_at = old.updated_at then
    new.updated_at = now();
  end if;
  return new;
end;
$$;

create or replace function public.reject_stale_sync_update()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  if new.updated_at < old.updated_at then
    return old;
  end if;
  return new;
end;
$$;

do $$
declare
  t text;
begin
  foreach t in array array[
    'user_profiles','shops','workers','months','pieces','days',
    'entries','individual_entries','individual_entry_items'
  ] loop
    execute format('drop trigger if exists reject_stale_sync_%I on public.%I', t, t);
    execute format(
      'create trigger reject_stale_sync_%I before update on public.%I for each row execute function public.reject_stale_sync_update()',
      t, t
    );
  end loop;
end $$;
