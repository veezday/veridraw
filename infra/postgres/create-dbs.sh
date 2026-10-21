#!/bin/bash
set -e
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE draw_core;
    CREATE DATABASE notify_worker;
EOSQL