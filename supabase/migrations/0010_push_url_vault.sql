-- The function URL moves from a database setting to Vault.
--
-- `alter database … set app.push_url` only reaches sessions created after the change,
-- and PostgREST's pooled connections are long-lived, so the trigger could stay inert for
-- a long time after the URL was configured. Reading the URL from Vault, like the shared
-- secret, takes effect on the next insert.

create or replace function public.notify_push()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  hook_url text;
  hook_secret text;
begin
  select decrypted_secret into hook_url
    from vault.decrypted_secrets
   where name = 'push_webhook_url';

  if hook_url is null or hook_url = '' then
    return new;
  end if;

  select decrypted_secret into hook_secret
    from vault.decrypted_secrets
   where name = 'push_webhook_secret';

  perform net.http_post(
    url := hook_url,
    body := jsonb_build_object('record', to_jsonb(new)),
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-webhook-secret', coalesce(hook_secret, '')
    ),
    timeout_milliseconds := 5000
  );

  return new;
end;
$$;

-- `messages.type` is 'default' for a normal message ('text' is a channel type); the
-- trigger installed in 0009 filtered on 'text' and therefore never fired.
drop trigger if exists messages_push on public.messages;
create trigger messages_push
  after insert on public.messages
  for each row
  when (new.type = 'default')
  execute function public.notify_push();
