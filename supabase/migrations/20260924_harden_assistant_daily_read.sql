-- Pepers: bind assistant daily reads to both the approved assistant link
-- and the tailor account that owns the record. This prevents record_key
-- collisions across different tailor accounts from exposing another user's data.

create or replace function public.my_assistant_daily()
returns jsonb language sql security definer set search_path=public
as $f$
select coalesce(jsonb_agg(jsonb_build_object(
 'id',d.id,'date',coalesce(nullif(split_part(d.record_key,':',3),''),''),
 'quantity',d.reported_quantity,'expense',d.expense,'note',d.notes,
 'status',d.status,'approval_status',d.approval_status
) order by d.updated_at desc), '[]'::jsonb)
from public.assistant_daily_records d
join public.assistant_link_requests r
  on r.assistant_user_id=auth.uid()
 and r.status='APPROVED'
join public.assistants a
  on a.id=r.assistant_id
 and a.record_key=d.assistant_record_key
 and a.user_id=d.user_id
where d.approval_status='APPROVED';

grant execute on function public.my_assistant_daily() to authenticated;
