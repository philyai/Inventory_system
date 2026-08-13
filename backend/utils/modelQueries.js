// Sequelize's MSSQL findOne implementation uses OFFSET/FETCH, which is not
// supported by the legacy SQL Server used by this project. Fetching matching
// rows without LIMIT and taking the first keeps these lookups compatible.
async function findFirst(Model, options = {}) {
  const rows = await Model.findAll(options);
  return rows[0] || null;
}

module.exports = { findFirst };
