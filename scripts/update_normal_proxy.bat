@echo off
REM Bulk-replace Dynamic Proxy Info of Normal tasks via the local backend API.
REM Keep update_normal_proxy.ps1 in the same folder. Shows a dry run first, then asks to apply.

set "PROXY=us.proxy.geonode.io:11000:geonode_Hk8qVHwrJb-country-us:d459c4b6-5c80-4f61-b7ac-9e4b203fcd42"
set "BASE_URL=http://localhost:8080"

set /p LOGIN_ID=Login phone number: 

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0update_normal_proxy.ps1" -LoginId "%LOGIN_ID%" -Proxy "%PROXY%" -BaseUrl "%BASE_URL%"

pause
