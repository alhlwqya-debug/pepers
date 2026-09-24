-- Adds the device identity field used by the Android sync client for user_profiles.
-- Safe for existing installations: IF NOT EXISTS prevents duplicate-column failures,
-- and the default allows existing rows to remain valid before their next sync.
alter table public.user_profiles
    add column if not exists source_device_id text not null default '';

-- Refresh PostgREST's schema cache so the new column is immediately visible to REST calls.
notify pgrst, 'reload schema';
