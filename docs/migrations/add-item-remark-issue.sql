IF OBJECT_ID('dbo.Item_Remark_Issue', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Item_Remark_Issue (
        issue_id INT IDENTITY(1,1) NOT NULL,
        issue_code AS (
            'ISS-' +
            CASE
                WHEN issue_id < 1000000
                    THEN RIGHT('000000' + CONVERT(VARCHAR(6), issue_id), 6)
                ELSE CONVERT(VARCHAR(20), issue_id)
            END
        ) PERSISTED,
        item_id INT NOT NULL,
        remarks NVARCHAR(500) NOT NULL,
        created_by INT NOT NULL,
        created_date DATETIME NOT NULL
            CONSTRAINT DF_ItemRemarkIssue_CreatedDate DEFAULT (GETDATE()),
        updated_date DATETIME NOT NULL
            CONSTRAINT DF_ItemRemarkIssue_UpdatedDate DEFAULT (GETDATE()),
        CONSTRAINT PK_ItemRemarkIssue PRIMARY KEY (issue_id),
        CONSTRAINT UQ_ItemRemarkIssue_IssueCode UNIQUE (issue_code),
        CONSTRAINT UQ_ItemRemarkIssue_Item UNIQUE (item_id),
        CONSTRAINT FK_ItemRemarkIssue_Item FOREIGN KEY (item_id)
            REFERENCES dbo.Items (item_id) ON DELETE CASCADE,
        CONSTRAINT FK_ItemRemarkIssue_CreatedBy FOREIGN KEY (created_by)
            REFERENCES dbo.users (users_id)
    );
END;

-- Rollback:
-- DROP TABLE dbo.Item_Remark_Issue;
