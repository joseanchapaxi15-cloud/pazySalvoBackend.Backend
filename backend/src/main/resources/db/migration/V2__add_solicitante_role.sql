-- Expand the existing role constraint for the applicant tracking account.
ALTER TABLE paz_salvo_user
    DROP CONSTRAINT IF EXISTS paz_salvo_user_role_check;

ALTER TABLE paz_salvo_user
    ADD CONSTRAINT paz_salvo_user_role_check
    CHECK (role IN ('TALENTO_HUMANO', 'RESPONSABLE_AREA', 'SOLICITANTE'));
