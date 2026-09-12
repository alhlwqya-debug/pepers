-- Profile images are private and scoped to the authenticated user.
insert into storage.buckets (id, name, public)
values ('profile-images', 'profile-images', false)
on conflict (id) do update set public = false;

drop policy if exists "profile_images_select_own" on storage.objects;
create policy "profile_images_select_own"
on storage.objects for select to authenticated
using (bucket_id = 'profile-images' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "profile_images_insert_own" on storage.objects;
create policy "profile_images_insert_own"
on storage.objects for insert to authenticated
with check (bucket_id = 'profile-images' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "profile_images_update_own" on storage.objects;
create policy "profile_images_update_own"
on storage.objects for update to authenticated
using (bucket_id = 'profile-images' and (storage.foldername(name))[1] = auth.uid()::text)
with check (bucket_id = 'profile-images' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "profile_images_delete_own" on storage.objects;
create policy "profile_images_delete_own"
on storage.objects for delete to authenticated
using (bucket_id = 'profile-images' and (storage.foldername(name))[1] = auth.uid()::text);
