# PostgreSQL Configuration

**Date:** 2025-03-26

## Connection Info

- **Host:** 192.168.5.202
- **Port:** 5432
- **User:** leon
- **Password:** ServBay.dev
- **Database:** yare_engine (to be created)

## Status

PostgreSQL 已启动，可用。需要在 Phase 2 开始前创建 `yare_engine` 数据库。

## Usage

```bash
# Create database
psql -h 192.168.5.202 -U leon -c "CREATE DATABASE yare_engine;"

# Connect
psql -h 192.168.5.202 -U leon -d yare_engine
```

**application.yml 配置:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://192.168.5.202:5432/yare_engine
    username: leon
    password: ServBay.dev
```
