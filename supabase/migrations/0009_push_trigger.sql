-- Phase 6: fan out new messages to the push Edge Function.
--
-- The function URL is a database setting so the trigger stays inert until the function
-- is deployed (`alter database postgres set app.push_url = …`). The shared secret lives
-- in Vault, not in the trigger body.

create extension if not exists pg_net;

do $$
begin
  if not exists (select 1 from vault.secrets where name = 'push_webhook_secret') then
    perform vault.create_secret(md5(random()::text || clock_timestamp()::text), 'push_webhook_secret');
  end if;
end $$;

create or replace function public.notify_push()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  hook_url text := current_setting('app.push_url', true);
  hook_secret text;
begin
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

drop trigger if exists messages_push on public.messages;
create trigger messages_push
  after insert on public.messages
  for each row
  when (new.type = 'text')
  execute function public.notify_push();
