-- Apply this migration before deploying the backend idempotency changes.
-- Existing rows remain unchanged because client_request_id is nullable.

IF COL_LENGTH('dbo.Items', 'client_request_id') IS NULL
BEGIN
  ALTER TABLE dbo.Items
    ADD client_request_id NVARCHAR(100) NULL;
END;
GO

IF NOT EXISTS (
  SELECT 1
  FROM sys.indexes
  WHERE name = 'UX_Items_CreatedBy_ClientRequestId'
    AND object_id = OBJECT_ID('dbo.Items')
)
BEGIN
  CREATE UNIQUE INDEX UX_Items_CreatedBy_ClientRequestId
    ON dbo.Items (created_by, client_request_id)
    WHERE client_request_id IS NOT NULL;
END;
GO

-- Rollback:
-- DROP INDEX UX_Items_CreatedBy_ClientRequestId ON dbo.Items;
-- ALTER TABLE dbo.Items DROP COLUMN client_request_id;
