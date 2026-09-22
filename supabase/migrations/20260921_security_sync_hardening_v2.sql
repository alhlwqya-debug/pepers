-- Pepers security/performance hardening follow-up.

revoke execute on function public.rls_auto_enable() from public, anon, authenticated;

do $$
begin
  if to_regclass('public.pepers_cloud_backups') is not null then
    drop policy if exists pepers_cloud_backups_select_own on public.pepers_cloud_backups;
    create policy pepers_cloud_backups_select_own on public.pepers_cloud_backups
      for select to authenticated
      using ((select auth.uid()) = user_id);

    drop policy if exists pepers_cloud_backups_insert_own on public.pepers_cloud_backups;
    create policy pepers_cloud_backups_insert_own on public.pepers_cloud_backups
      for insert to authenticated
      with check ((select auth.uid()) = user_id);

    drop policy if exists pepers_cloud_backups_update_own on public.pepers_cloud_backups;
    create policy pepers_cloud_backups_update_own on public.pepers_cloud_backups
      for update to authenticated
      using ((select auth.uid()) = user_id)
      with check ((select auth.uid()) = user_id);
  end if;
end $$;
