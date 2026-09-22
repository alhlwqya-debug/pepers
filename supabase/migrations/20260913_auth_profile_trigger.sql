-- Pepers: keep the cloud profile synchronized with Supabase Auth metadata.
-- This does not replace the Android local/offline database. It only guarantees
-- that a newly created Auth user gets an isolated profile row immediately.

create or replace function public.handle_new_pepers_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.user_profiles (
        user_id,
        display_name,
        phone,
        email
    )
    values (
        new.id,
        coalesce(
            nullif(new.raw_user_meta_data ->> 'full_name', ''),
            nullif(new.raw_user_meta_data ->> 'name', ''),
            ''
        ),
        coalesce(new.raw_user_meta_data ->> 'phone', ''),
        coalesce(new.email, '')
    )
    on conflict (user_id) do update set
        display_name = case
            when public.user_profiles.display_name = ''
                then excluded.display_name
            else public.user_profiles.display_name
        end,
        phone = case
            when public.user_profiles.phone = ''
                then excluded.phone
            else public.user_profiles.phone
        end,
        email = excluded.email,
        updated_at = now();

    return new;
end;
$$;

drop trigger if exists on_auth_user_created_pepers on auth.users;

create trigger on_auth_user_created_pepers
after insert on auth.users
for each row
execute function public.handle_new_pepers_user();

-- Backfill profiles for Auth users that were created before this migration.
insert into public.user_profiles (user_id, display_name, phone, email)
select
    u.id,
    coalesce(
        nullif(u.raw_user_meta_data ->> 'full_name', ''),
        nullif(u.raw_user_meta_data ->> 'name', ''),
        ''
    ),
    coalesce(u.raw_user_meta_data ->> 'phone', ''),
    coalesce(u.email, '')
from auth.users u
on conflict (user_id) do update set
    display_name = case
        when public.user_profiles.display_name = ''
            then excluded.display_name
        else public.user_profiles.display_name
    end,
    phone = case
        when public.user_profiles.phone = ''
            then excluded.phone
        else public.user_profiles.phone
    end,
    email = excluded.email,
    updated_at = now();

-- The trigger is security-definer because it runs from auth.users.
-- Keep its execution surface minimal and do not grant direct execution to clients.
revoke all on function public.handle_new_pepers_user() from public;
