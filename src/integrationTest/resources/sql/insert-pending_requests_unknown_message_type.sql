INSERT INTO public.pending_requests
(id, hearing_id, version_number, message_type, submitted_date_time,
 deployment_id, retry_count, last_tried_date_time, status, incident_flag,
 message)
VALUES
(1, 2000000000, 1, 'UNKNOWN_TYPE', now() AT TIME ZONE 'UTC' - INTERVAL '20 minute',
 'depId01', 0, now() AT TIME ZONE 'UTC' - INTERVAL '20 minute', 'PENDING', false,
 '{"test":"unknown message type"}');
