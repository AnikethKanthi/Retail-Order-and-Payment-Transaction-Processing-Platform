-- Run only after role provisioning and database/schema/runtime-grants.sql.
DO $$
DECLARE
    app_role TEXT;
    owned_schema TEXT;
    schema_name TEXT;
    relation RECORD;
    owns_table BOOLEAN;
BEGIN
    FOREACH app_role IN ARRAY ARRAY['retail_order_app', 'retail_payment_app', 'retail_inventory_app'] LOOP
        owned_schema := CASE app_role
            WHEN 'retail_order_app' THEN 'order_service'
            WHEN 'retail_payment_app' THEN 'payment_service'
            ELSE 'inventory_service' END;
        IF has_database_privilege(app_role, current_database(), 'CREATE') THEN
            RAISE EXCEPTION '% has database CREATE privileges', app_role;
        END IF;
        FOREACH schema_name IN ARRAY ARRAY['order_service', 'payment_service', 'inventory_service', 'platform_migrations'] LOOP
            IF has_schema_privilege(app_role, schema_name, 'USAGE') <> (schema_name = owned_schema)
                OR has_schema_privilege(app_role, schema_name, 'CREATE') THEN
                RAISE EXCEPTION 'Unexpected schema access for % on %', app_role, schema_name;
            END IF;
        END LOOP;
        FOR relation IN
            SELECT n.nspname, c.relname FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname IN ('order_service', 'payment_service', 'inventory_service', 'platform_migrations')
                AND c.relkind = 'r'
        LOOP
            owns_table := relation.nspname = owned_schema;
            IF has_table_privilege(app_role, format('%I.%I', relation.nspname, relation.relname), 'SELECT') <> owns_table
                OR has_table_privilege(app_role, format('%I.%I', relation.nspname, relation.relname), 'INSERT') <> owns_table
                OR has_table_privilege(app_role, format('%I.%I', relation.nspname, relation.relname), 'UPDATE') <>
                    (owns_table AND relation.relname <> 'transaction_records')
                OR has_table_privilege(app_role, format('%I.%I', relation.nspname, relation.relname), 'DELETE')
                OR has_table_privilege(app_role, format('%I.%I', relation.nspname, relation.relname), 'TRUNCATE')
                OR has_table_privilege(app_role, format('%I.%I', relation.nspname, relation.relname), 'TRIGGER') THEN
                RAISE EXCEPTION 'Unexpected table access for % on %.%', app_role, relation.nspname, relation.relname;
            END IF;
        END LOOP;
    END LOOP;
END;
$$;
