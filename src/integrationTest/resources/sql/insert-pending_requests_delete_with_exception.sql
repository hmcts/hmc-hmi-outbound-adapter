INSERT INTO public.pending_requests (
    hearing_id, message, message_type, status, incident_flag, version_number, last_tried_date_time, submitted_date_time, retry_count, deployment_id)
VALUES (2000000001, 'Completed', 'REQUEST_HEARING', 'COMPLETED', false, 1, '2023-10-01 10:00:00', '2023-10-01 10:00:00', 0, 'depId01');
INSERT INTO public.pending_requests (
    hearing_id, message, message_type, status, incident_flag, version_number, last_tried_date_time, submitted_date_time, retry_count, deployment_id)
VALUES (2000000001, 'Completed', 'AMEND_HEARING', 'EXCEPTION', false, 1, '2023-10-01 10:20:00', '2023-10-01 10:20:00', 0, 'depId01');
INSERT INTO public.pending_requests (
    hearing_id, message, message_type, status, incident_flag, version_number, last_tried_date_time, submitted_date_time, retry_count, deployment_id)
VALUES (2000000001, 'Not this one', 'DELETE_HEARING', 'PENDING', false, 1, '2023-10-01 10:40:00', '2023-10-01 10:40:00', 0, 'depId01');
INSERT INTO public.pending_requests (
    hearing_id, message, message_type, status, incident_flag, version_number, last_tried_date_time, submitted_date_time, retry_count, deployment_id)
VALUES (2000000002, 'But this one', 'REQUEST_HEARING', 'PENDING', false, 1, '2023-10-01 11:00:00', '2023-10-01 11:00:00', 0, 'depId02');
