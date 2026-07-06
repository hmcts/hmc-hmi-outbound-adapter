INSERT INTO public.pending_requests
(id, hearing_id, version_number, message_type, submitted_date_time,
 deployment_id, retry_count, last_tried_date_time, status, incident_flag,
 message)
VALUES
(1, 2000000000, 1, 'REQUEST_HEARING', now() - INTERVAL '25 hour',
 'depId01', 0, now() - INTERVAL '20 minute', 'PENDING', false,
 '{"test":"submitted time exceeded, last tried time exceeded"}');

INSERT INTO public.pending_requests
(id, hearing_id, version_number, message_type, submitted_date_time,
 deployment_id, retry_count, last_tried_date_time, status, incident_flag,
 message)
VALUES
(2, 2000000001, 1, 'REQUEST_HEARING', now() - INTERVAL '23 hour',
 'depId01', 0, now() - INTERVAL '10 minute', 'PENDING', false,
 '{"test":"submitted time not exceeded, last tried time not exceeded"}');

INSERT INTO public.pending_requests
(id, hearing_id, version_number, message_type, submitted_date_time,
 deployment_id, retry_count, last_tried_date_time, status, incident_flag,
 message)
VALUES
(3, 2000000002, 1, 'REQUEST_HEARING', now() - INTERVAL '25 hour',
 'depId01', 0, now() - INTERVAL '10 minute', 'PENDING', false,
 '{"test":"submitted time exceeded, last tried time not exceeded"}');
