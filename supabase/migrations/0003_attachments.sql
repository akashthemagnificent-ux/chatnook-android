-- Phase 2: media messages.
--
-- Two things are needed and both are schema, not client: a bucket for message
-- images, and an atomic send path. The send path is an RPC on purpose — inserting the
-- message and its attachment as two REST calls lets Realtime fire the message INSERT
-- before the attachment row exists, so every other member renders a picture-less
-- message until they reload. One transaction means one event with the image attached.

-- ---------------------------------------------------------------- storage

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('attachments', 'attachments', true, 8388608,
        array['image/png', 'image/jpeg', 'image/gif', 'image/webp'])
on conflict (id) do update
  set public             = excluded.public,
      file_size_limit    = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- Public read keeps the client simple (Coil fetches the URL directly). The trade-off:
-- anyone holding a URL can read that image, exactly like Discord's CDN.
drop policy if exists chatnook_attachments_read on storage.objects;
create policy chatnook_attachments_read on storage.objects
  for select to public using (bucket_id = 'attachments');

drop policy if exists chatnook_attachments_insert on storage.objects;
create policy chatnook_attachments_insert on storage.objects
  for insert to authenticated with check (
    bucket_id = 'attachments'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists chatnook_attachments_delete on storage.objects;
create policy chatnook_attachments_delete on storage.objects
  for delete to authenticated using (
    bucket_id = 'attachments'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

-- ---------------------------------------------------------------- send_message

create or replace function public.send_message(
  p_channel_id  uuid,
  p_content     text,
  p_message_id  uuid default gen_random_uuid(),
  p_reply_to_id uuid default null,
  p_attachment  jsonb default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  me  uuid := auth.uid();
  mid uuid := coalesce(p_message_id, gen_random_uuid());
  body text := nullif(btrim(coalesce(p_content, '')), '');
begin
  if me is null then raise exception 'not authenticated'; end if;
  if not public.can_access_channel(p_channel_id) then
    raise exception 'no access to channel';
  end if;
  if body is null and p_attachment is null then
    raise exception 'message has no content';
  end if;

  -- Trust boundary: the client supplies these, so refuse anything malformed rather
  -- than writing a row the renderer cannot use.
  if p_attachment is not null then
    if coalesce(p_attachment ->> 'url', '') = '' then
      raise exception 'attachment url missing';
    end if;
    if (p_attachment ->> 'size_bytes')::bigint > 8388608 then
      raise exception 'attachment too large';
    end if;
  end if;

  insert into public.messages (id, channel_id, author_id, content, reply_to_id)
  values (mid, p_channel_id, me, body, p_reply_to_id);

  if p_attachment is not null then
    insert into public.attachments
      (message_id, url, filename, content_type, size_bytes, width, height)
    values (
      mid,
      p_attachment ->> 'url',
      p_attachment ->> 'filename',
      p_attachment ->> 'content_type',
      (p_attachment ->> 'size_bytes')::bigint,
      (p_attachment ->> 'width')::int,
      (p_attachment ->> 'height')::int
    );
  end if;

  return mid;
end;
$$;

revoke all on function public.send_message(uuid, text, uuid, uuid, jsonb) from public;
grant execute on function public.send_message(uuid, text, uuid, uuid, jsonb) to authenticated;
