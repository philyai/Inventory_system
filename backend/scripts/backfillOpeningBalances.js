require('dotenv').config();

const sequelize = require('../models');

const shouldApply = process.argv.includes('--apply');

async function getEligibleCount(transaction) {
  const [rows] = await sequelize.query(`
    SELECT COUNT(*) AS eligible_count
    FROM Items i
    WHERE ISNULL(i.quantity, 0) > 0
      AND NOT EXISTS (
        SELECT 1
        FROM Item_Movement m
        WHERE m.item_id = i.item_id
      )
  `, { transaction });

  return Number(rows[0].eligible_count);
}

async function backfillOpeningBalances() {
  await sequelize.authenticate();

  if (!shouldApply) {
    const eligibleCount = await getEligibleCount();
    console.log(`Preview: ${eligibleCount} item(s) are eligible for an opening balance.`);
    console.log('Run with --apply to insert the movement records.');
    return;
  }

  const insertedCount = await sequelize.transaction(async transaction => {
    const [, metadata] = await sequelize.query(`
      INSERT INTO Item_Movement (
        item_id,
        movement_type,
        quantity_change,
        source_destination,
        remarks,
        processed_by,
        movement_date
      )
      SELECT
        i.item_id,
        'In',
        i.quantity,
        'Opening balance',
        'Opening balance for existing inventory',
        i.created_by,
        GETDATE()
      FROM Items i WITH (UPDLOCK, HOLDLOCK)
      WHERE ISNULL(i.quantity, 0) > 0
        AND NOT EXISTS (
          SELECT 1
          FROM Item_Movement m WITH (UPDLOCK, HOLDLOCK)
          WHERE m.item_id = i.item_id
        );
    `, { transaction });

    return metadata;
  });

  console.log(`Backfill complete: ${insertedCount} opening balance movement(s) inserted.`);
}

backfillOpeningBalances()
  .catch(error => {
    console.error('Opening balance backfill failed:', error.message);
    process.exitCode = 1;
  })
  .finally(async () => {
    await sequelize.close();
  });
