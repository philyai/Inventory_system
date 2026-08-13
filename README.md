# Inventory System

This repository contains the backend API and Android frontend for the Inventory System.

## Project structure

```text
Inventory_system/
|-- backend/   Node.js, Express, Sequelize, and SQL Server REST API
`-- frontend/  Android application built with Java and Gradle
```

## Backend

Requirements:

- Node.js and npm
- Microsoft SQL Server

From the repository root:

```powershell
cd backend
npm install
npm run dev
```

Create `backend/.env` locally with the required database and application settings:

```dotenv
DB_NAME=
DB_USER=
DB_PASSWORD=
DB_HOST=
DB_PORT=
JWT_SECRET=
PORT=3001
```

Do not commit the `.env` file. Run the backend tests with:

```powershell
npm test
```

## Android frontend

Open the `frontend` directory as a project in Android Studio. Configure the local Android SDK through `local.properties`, then ensure the app uses a backend address reachable from the emulator or physical device.

Run the Android unit tests from the repository root with:

```powershell
cd frontend
.\gradlew.bat testDebugUnitTest
```

## Files excluded from Git

Local credentials, signing keys, dependency directories, IDE caches, generated builds, and runtime uploads are intentionally excluded from the repository.
