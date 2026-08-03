-- Preserve existing disposal records as single-unit requests.
IF COL_LENGTH('dbo.Disposal', 'disposal_quantity') IS NULL
BEGIN
    ALTER TABLE dbo.Disposal
    ADD disposal_quantity INT NOT NULL
        CONSTRAINT DF_Disposal_DisposalQuantity DEFAULT (1);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'CK_Disposal_DisposalQuantity_Positive'
      AND parent_object_id = OBJECT_ID('dbo.Disposal')
)
BEGIN
    ALTER TABLE dbo.Disposal
    ADD CONSTRAINT CK_Disposal_DisposalQuantity_Positive
        CHECK (disposal_quantity > 0);
END;

-- Rollback:
-- ALTER TABLE dbo.Disposal DROP CONSTRAINT CK_Disposal_DisposalQuantity_Positive;
-- ALTER TABLE dbo.Disposal DROP CONSTRAINT DF_Disposal_DisposalQuantity;
-- ALTER TABLE dbo.Disposal DROP COLUMN disposal_quantity;
