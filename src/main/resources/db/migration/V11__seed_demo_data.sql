-- Categorías de Automatización y Suministros Industriales
INSERT INTO category (id, name, description) VALUES
    (1, 'Controladores y PLCs Industriales', 'Autómatas programables, módulos de E/S y CPUs de control'),
    (2, 'Variadores de Frecuencia y Arrancadores', 'Accionamientos de velocidad variable y arrancadores suaves para motores'),
    (3, 'Sensores y Transmisores de Proceso', 'Sensores inductivos, fotoeléctricos, presión y flujo de grado industrial'),
    (4, 'Protección y Distribución Eléctrica', 'Interruptores en caja moldeada, contactores y protecciones térmicas');

-- Bodegas Físicas Regionales (Nodos de Distribución)
INSERT INTO warehouse (id, code, name, address) VALUES
    (1, 'WH-SCL-CENTRAL', 'Centro de Distribución Central Pudahuel', 'Av. Américo Vespucio 1500, Pudahuel, Región Metropolitana, Chile'),
    (2, 'WH-VAP-PORT', 'Hub Logístico Portuario Valparaíso', 'Av. Errázuriz 890, Valparaíso, Región de Valparaíso, Chile'),
    (3, 'WH-ANF-MINER', 'Hub de Abastecimiento Minero Antofagasta', 'Av. Pedro Aguirre Cerda 4500, Antofagasta, Región de Antofagasta, Chile');

-- Catálogo de SKUs Críticos
INSERT INTO product (id, sku, name, technical_spec, unit_price, category_id, active, version) VALUES
    (1, 'PLC-SIEM-S71500', 'Siemens SIMATIC S7-1500 CPU 1516-3 PN/DP', '1 MB código, 5 MB datos, interfaces PROFINET / PROFIBUS', 1850000.00, 1, true, 0),
    (2, 'PLC-AB-1756L83', 'Allen-Bradley ControlLogix 5580 Controller', 'Memoria 10MB, puerto Ethernet 1 Gbps integrado, soporte CIP Safety', 2450000.00, 1, true, 0),
    (3, 'VAR-ABB-ACS580', 'Variador de Frecuencia ABB ACS580-01-039A-4', 'Potencia 18.5 kW / 25 HP, 400V trifásico, IP21, panel asistente', 1320000.00, 2, true, 0),
    (4, 'SEN-IFM-IND01', 'Sensor Inductivo IFM Efector IGS204', 'Rango 8 mm, M18, salida PNP/NPN configurable, conector M12, IP68', 68000.00, 3, true, 0),
    (5, 'SW-SCH-NSX250', 'Interruptor Caja Moldeada Schneider ComPact NSX250F', '250A, 3P3D, poder de corte 36kA a 415V, unidad Micrologic 2.2', 420000.00, 4, true, 0);

-- Stock Inicial en Bodega Central (WH-SCL-CENTRAL, id=1)
INSERT INTO stock_level (product_id, warehouse_id, total_qty, reserved_qty) VALUES
    (1, 1, 10, 0),
    (2, 1, 5, 0),
    (3, 1, 12, 0),
    (4, 1, 50, 0),
    (5, 1, 20, 0);

-- Stock Inicial en Bodega Minera Antofagasta (WH-ANF-MINER, id=3)
INSERT INTO stock_level (product_id, warehouse_id, total_qty, reserved_qty) VALUES
    (1, 3, 3, 0),
    (2, 3, 2, 0),
    (3, 3, 6, 0),
    (4, 3, 25, 0);

-- Inbound Ledger Entries de Auditoría Inicial
INSERT INTO inventory_ledger (product_id, warehouse_id, order_id, movement_type, quantity_delta, resulting_total, resulting_reserved, reason, performed_by) VALUES
    (1, 1, NULL, 'INBOUND', 10, 10, 0, 'Recepción Inicial Importación Siemens Alemania Lote #2026-A1', 'SYSTEM_INITIAL_SEED'),
    (2, 1, NULL, 'INBOUND', 5, 5, 0, 'Recepción Inicial Rockwell Automation Lote #2026-R1', 'SYSTEM_INITIAL_SEED'),
    (3, 1, NULL, 'INBOUND', 12, 12, 0, 'Recepción Inicial Variadores ABB Lote #2026-B4', 'SYSTEM_INITIAL_SEED'),
    (4, 1, NULL, 'INBOUND', 50, 50, 0, 'Recepción Lote Sensores IFM Alemania #2026-S1', 'SYSTEM_INITIAL_SEED'),
    (5, 1, NULL, 'INBOUND', 20, 20, 0, 'Recepción Schneider Electric Lote #2026-E5', 'SYSTEM_INITIAL_SEED'),
    (1, 3, NULL, 'INBOUND', 3, 3, 0, 'Stock de Seguridad Minera Antofagasta', 'SYSTEM_INITIAL_SEED'),
    (2, 3, NULL, 'INBOUND', 2, 2, 0, 'Stock de Seguridad Minera Antofagasta', 'SYSTEM_INITIAL_SEED'),
    (3, 3, NULL, 'INBOUND', 6, 6, 0, 'Stock de Seguridad Minera Antofagasta', 'SYSTEM_INITIAL_SEED'),
    (4, 3, NULL, 'INBOUND', 25, 25, 0, 'Stock de Seguridad Minera Antofagasta', 'SYSTEM_INITIAL_SEED');

-- Sincronización de secuencias de identidad post-seed
SELECT setval(pg_get_serial_sequence('category', 'id'), COALESCE(MAX(id), 1)) FROM category;
SELECT setval(pg_get_serial_sequence('warehouse', 'id'), COALESCE(MAX(id), 1)) FROM warehouse;
SELECT setval(pg_get_serial_sequence('product', 'id'), COALESCE(MAX(id), 1)) FROM product;
SELECT setval(pg_get_serial_sequence('stock_level', 'id'), COALESCE(MAX(id), 1)) FROM stock_level;
SELECT setval(pg_get_serial_sequence('inventory_ledger', 'id'), COALESCE(MAX(id), 1)) FROM inventory_ledger;
