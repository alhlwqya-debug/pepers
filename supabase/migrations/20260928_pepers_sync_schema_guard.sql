-- Pepers sync schema guard.
-- Keeps existing Supabase projects compatible with the Android sync client even
-- when an older migration was applied without refreshing PostgREST's schema cache.
-- Safe to run repeatedly.

create extension if not exists pgcrypto;

do $$
declare
    t text;
begin
    foreach t in array array[
        'shops', 'workers', 'months', 'pieces', 'days', 'entries',
        'individual_entries', 'individual_entry_items', 'assistants',
        'assistant_piece_rates', 'assistant_daily_records', 'assistant_withdrawals'
    ] loop
        if to_regclass('public.' || t) is not null then
            execute format(
                'alter table public.%I add column if not exists record_key text not null default gen_random_uuid()::text',
                t
            );
            execute format(
                'alter table public.%I add column if not exists source_device_id text not null default ''''',
                t
            );
        end if;
    end loop;
end $$;

alter table if exists public.user_profiles
    add column if not exists record_key text not null default 'profile';

alter table if exists public.user_profiles
    add column if not exists source_device_id text not null default '';

-- PostgREST can keep an old schema snapshot after DDL has succeeded.
notify pgrst, 'reload schema';
