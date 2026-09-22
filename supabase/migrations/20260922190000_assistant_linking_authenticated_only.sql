-- Pepers: assistant linking security hardening.
-- Assistant linking and shared daily-work RPCs require an authenticated user.
-- These functions intentionally use SECURITY DEFINER for RLS-safe shared access,
-- but must never be callable anonymously.

revoke execute on function public.request_assistant_link(text) from anon;
revoke execute on function public.approve_assistant_link(uuid,boolean) from anon;
revoke execute on function public.my_assistant_link() from anon;
revoke execute on function public.pending_assistant_links() from anon;
revoke execute on function public.upsert_my_assistant_daily(text,integer,integer,text) from anon;
revoke execute on function public.pending_assistant_daily() from anon;
revoke execute on function public.approve_assistant_daily(uuid,boolean,integer,integer) from anon;
revoke execute on function public.my_assistant_daily() from anon;
