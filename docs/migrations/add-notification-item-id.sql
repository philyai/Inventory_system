IF COL_LENGTH('dbo.Notifications', 'item_id') IS NULL
BEGIN
    ALTER TABLE dbo.Notifications
    ADD item_id INT NULL;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'FK_Notifications_Item'
      AND parent_object_id = OBJECT_ID('dbo.Notifications')
)
BEGIN
    ALTER TABLE dbo.Notifications WITH CHECK
    ADD CONSTRAINT FK_Notifications_Item FOREIGN KEY (item_id)
        REFERENCES dbo.Items (item_id) ON DELETE SET NULL;
END;

-- Rollback:
-- ALTER TABLE dbo.Notifications DROP CONSTRAINT FK_Notifications_Item;
-- ALTER TABLE dbo.Notifications DROP COLUMN item_id;
