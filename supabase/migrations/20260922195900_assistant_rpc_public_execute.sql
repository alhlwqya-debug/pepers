-- Pepers: remove default PUBLIC execute on assistant RPCs.
-- Keep these SECURITY DEFINER functions available only to signed-in users.

revoke execute on function public.request_assistant_link(text) from public;
revoke execute on function public.approve_assistant_link(uuid,boolean) from public;
revoke execute on function public.my_assistant_link() from public;
revoke execute on function public.pending_assistant_links() from public;
revoke execute on function public.upsert_my_assistant_daily(text,integer,integer,text) from public;
revoke execute on function public.pending_assistant_daily() from public;
revoke execute on function public.approve_assistant_daily(uuid,boolean,integer,integer) from public;
revoke execute on function public.my_assistant_daily() from public;

grant execute on function public.request_assistant_link(text) to authenticated;
grant execute on function public.approve_assistant_link(uuid,boolean) to authenticated;
grant execute on function public.my_assistant_link() to authenticated;
grant execute on function public.pending_assistant_links() to authenticated;
grant execute on function public.upsert_my_assistant_daily(text,integer,integer,text) to authenticated;
grant execute on function public.pending_assistant_daily() to authenticated;
grant execute on function public.approve_assistant_daily(uuid,boolean,integer,integer) to authenticated;
grant execute on function public.my_assistant_daily() to authenticated;
