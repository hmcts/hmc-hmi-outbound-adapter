INSERT INTO public.pending_requests
(id, hearing_id, version_number, message_type, submitted_date_time,
 deployment_id, retry_count, last_tried_date_time, status, incident_flag,
 message)
VALUES
(1, 2000000000, 1, 'REQUEST_HEARING', '2026-02-01 12:00:00',
 'depId01', 0, '2026-02-01 12:00:00', 'PROCESSING', false,
 '{"test":"request to be updated"}');

INSERT INTO public.pending_requests
(id, hearing_id, version_number, message_type, submitted_date_time,
 deployment_id, retry_count, last_tried_date_time, status, incident_flag,
 message)
VALUES
(2, 2000000001, 1, 'AMEND_HEARING', '2026-02-02 13:00:00',
 'depId01', 0, '2026-02-02 13:00:00', 'PROCESSING', false,
 '{"test":"request not to be updated"}');
