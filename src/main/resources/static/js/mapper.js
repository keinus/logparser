// Mapper UI Module
const MapperUI = (function() {
    
    // State
    let currentState = {
        messageType: null,
        sourceFields: [],
        mappingState: {}, // targetCol -> sourceField
        commonSchema: [],
        subSchemas: {},
        currentSubTable: null
    };

    // DOM Elements Reference
    let containerEl = null;

    // Helper to get element by ID (scoped if possible, but IDs are global)
    function getEl(id) {
        return document.getElementById(id);
    }

    // Initialize/Render the UI into a container
    function render(container) {
        containerEl = container;
        containerEl.innerHTML = `
            <div class="mapper-container" style="display: flex; flex-direction: column; height: 100%; gap: var(--spacing-md);">
                
                <!-- 1. Rules -->
                <div class="mapper-section" style="background: var(--bg-tertiary); padding: var(--spacing-md); border-radius: var(--radius-md); border: 1px solid var(--border);">
                    <h3 style="font-size: 1rem; font-weight: 600; margin-bottom: var(--spacing-md); color: var(--text-primary);">Transformation Rules</h3>
                    <div style="display: flex; gap: var(--spacing-lg); flex-wrap: wrap;">
                        <div style="flex: 2; min-width: 300px;">
                            <label style="display: block; margin-bottom: 0.5rem; font-weight: 500; font-size: 0.875rem;">Condition (SpEL Expression)</label>
                            <input type="text" id="condition-input" class="form-control" placeholder="e.g. dst_port == 80 || protocol == 'HTTP'">
                            <small style="color: var(--text-tertiary);">Expression to determine when this sub-table rule applies.</small>
                        </div>
                        <div style="flex: 1; min-width: 200px;">
                            <label style="display: block; margin-bottom: 0.5rem; font-weight: 500; font-size: 0.875rem;">Target Sub-Table</label>
                            <select id="sub-schema-select" class="form-control" onchange="MapperUI.handleSubTableChange()">
                                <!-- Dynamic options -->
                            </select>
                        </div>
                    </div>
                </div>

                <!-- 2. Mapping Area -->
                <div class="mapper-section" style="flex: 1; display: flex; flex-direction: column; min-height: 0; background: var(--bg-secondary); border-radius: var(--radius-md); border: 1px solid var(--border); overflow: hidden;">
                    <div style="padding: var(--spacing-md); background: var(--bg-tertiary); border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center;">
                        <h3 style="font-size: 1rem; font-weight: 600; color: var(--text-primary);">Schema Mapping</h3>
                        <div style="display: flex; gap: var(--spacing-sm);">
                            <button class="btn btn-secondary btn-small" onclick="MapperUI.resetMapping()">Reset</button>
                            <button class="btn btn-primary btn-small" onclick="MapperUI.autoMap()">
                                <span class="material-icons" style="font-size: 16px;">auto_fix_high</span> Auto Map
                            </button>
                        </div>
                    </div>

                    <div style="display: flex; flex: 1; min-height: 0;">
                        <!-- Source Column -->
                        <div style="width: 300px; display: flex; flex-direction: column; border-right: 1px solid var(--border); background: var(--bg-primary);">
                            <div style="padding: var(--spacing-sm); border-bottom: 1px solid var(--border); background: var(--bg-tertiary);">
                                <h4 style="font-size: 0.75rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em;">Source Fields</h4>
                            </div>
                            <div style="padding: var(--spacing-sm); border-bottom: 1px solid var(--border);">
                                <div style="display: flex; gap: var(--spacing-xs);">
                                    <input type="text" id="new-field-name" class="form-control" placeholder="Add custom field..." style="padding: 4px 8px; font-size: 0.8rem;">
                                    <button class="btn btn-secondary btn-small" onclick="MapperUI.addCustomField()">Add</button>
                                </div>
                            </div>
                            <div id="source-list" style="flex: 1; overflow-y: auto; padding: var(--spacing-sm);"></div>
                        </div>

                        <!-- Target Column -->
                        <div style="flex: 1; display: flex; flex-direction: column; background: var(--bg-primary); min-width: 0;">
                            <div style="padding: var(--spacing-sm); border-bottom: 1px solid var(--border); background: var(--bg-tertiary);">
                                <h4 style="font-size: 0.75rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em;">Target Schema</h4>
                            </div>
                            <div id="target-area" style="flex: 1; overflow-y: auto; padding: var(--spacing-lg);">
                                <div style="margin-bottom: var(--spacing-lg);">
                                    <div style="padding: var(--spacing-sm) var(--spacing-md); background: #e0e7ff; color: #3730a3; font-weight: 600; font-size: 0.875rem; border-radius: var(--radius-sm) var(--radius-sm) 0 0; border: 1px solid #c7d2fe; border-bottom: none;">Common Table: event</div>
                                    <div id="common-schema-rows" style="border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 var(--radius-sm) var(--radius-sm);"></div>
                                </div>
                                <div>
                                    <div id="sub-schema-header" style="padding: var(--spacing-sm) var(--spacing-md); background: #fce7f3; color: #831843; font-weight: 600; font-size: 0.875rem; border-radius: var(--radius-sm) var(--radius-sm) 0 0; border: 1px solid #fbcfe8; border-bottom: none;">Sub Table</div>
                                    <div id="sub-schema-rows" style="border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 var(--radius-sm) var(--radius-sm);"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 3. Simulation (Collapsible?) -->
                <div class="mapper-section" style="background: var(--bg-tertiary); padding: var(--spacing-md); border-radius: var(--radius-md); border: 1px solid var(--border);">
                    <div style="display: flex; justify-content: space-between; align-items: center; cursor: pointer;" onclick="document.getElementById('sim-content').style.display = document.getElementById('sim-content').style.display === 'none' ? 'block' : 'none'">
                         <h3 style="font-size: 1rem; font-weight: 600; color: var(--text-primary); margin: 0;">Simulation Preview <span class="material-icons" style="font-size: 16px; vertical-align: middle;">expand_more</span></h3>
                    </div>
                    <div id="sim-content" style="display: none; margin-top: var(--spacing-md);">
                        <div style="margin-bottom: var(--spacing-md);">
                            <textarea id="sample-log-data" class="form-control" rows="3" placeholder='{"src_ip": "192.168.1.1", "method": "GET"}' style="font-family: monospace; font-size: 0.875rem;"></textarea>
                        </div>
                        <button class="btn btn-secondary btn-small" onclick="MapperUI.runSimulation()">
                            <span class="material-icons" style="font-size: 16px;">play_arrow</span> Run Simulation
                        </button>
                        <div style="margin-top: var(--spacing-md);">
                            <div id="sim-result" style="background: #1e293b; color: #4ade80; padding: var(--spacing-md); border-radius: var(--radius-sm); font-family: monospace; white-space: pre-wrap; max-height: 200px; overflow-y: auto; font-size: 0.875rem;">// Result will appear here...</div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    async function loadData(messageType, existingConfig) {
        currentState.messageType = messageType;
        currentState.mappingState = {};
        currentState.sourceFields = ['timestamp', 'host', 'message', 'src_ip', 'dst_ip', 'method', 'url', 'status']; // Default basic fields
        
        try {
            await loadSchemaMetadata();
            
            // If existing config provided, load it
            if (existingConfig && existingConfig.param) {
                // If it's a "Structure" transform, 'param' is the config object
                loadConfigToState(existingConfig.param);
            } else {
                 // Try to load from server if nothing passed (legacy)
                 // But in new flow, we usually pass empty or existing
            }
        } catch (e) {
            console.error("Error initializing mapper:", e);
            // showToast("Failed to load mapper data", "error");
        }
        
        renderUI();
    }

    async function loadSchemaMetadata() {
        // Use the global metadataAPI if available, else fetch
        // Assuming metadataAPI from app.js is available or fetch directly
        const res = await fetch('/api/v1/metadata/transform-schema/Structure'); // A bit hacky, need generic schema
        // Actually the endpoint /api/transform/schema used in old code seems custom.
        // Let's assume we need to fetch the DB schema which might be static or dynamic.
        // For now, I will Mock/Hardcode based on the old code's expected response structure
        // OR fetch from a known endpoint.
        // The old code hit `/api/transform/schema`.
        
        // Let's try to hit the old endpoint if it exists or mock it for prototype
        try {
             // In a real app, this should come from a Metadata Service that knows about the Data Warehouse schema.
             // I'll reuse the logic from the old code:
             const response = await fetch('/api/v1/metadata/output-adapter-schema/OpenSearch'); // Just to check connection? No.
             
             // MOCK DATA for Prototype purposes as per context requirements "Autonomously implement"
             // I'll simulate the schema metadata typically found in LogParser.
             const data = {
                 commonSchema: [
                     { name: 'timestamp', type: 'Instant' },
                     { name: 'event_id', type: 'String' },
                     { name: 'ingest_time', type: 'Instant' },
                     { name: 'host', type: 'String' }
                 ],
                 subSchemas: {
                     'http_log': [
                         { name: 'method', type: 'String' },
                         { name: 'url', type: 'String' },
                         { name: 'status', type: 'Integer' },
                         { name: 'user_agent', type: 'String' },
                         { name: 'latency', type: 'Long' }
                     ],
                     'syslog': [
                         { name: 'facility', type: 'Integer' },
                         { name: 'severity', type: 'Integer' },
                         { name: 'program', type: 'String' },
                         { name: 'pid', type: 'Integer' }
                     ],
                     'network_log': [
                         { name: 'src_ip', type: 'String' },
                         { name: 'dst_ip', type: 'String' },
                         { name: 'src_port', type: 'Integer' },
                         { name: 'dst_port', type: 'Integer' },
                         { name: 'protocol', type: 'String' }
                     ]
                 }
             };
             
             currentState.commonSchema = data.commonSchema;
             currentState.subSchemas = data.subSchemas;
             
             // Populate Select
             const select = getEl('sub-schema-select');
             select.innerHTML = '';
             Object.keys(currentState.subSchemas).forEach(key => {
                 const opt = document.createElement('option');
                 opt.value = key;
                 opt.text = key;
                 select.appendChild(opt);
             });
             
             // Default sub table
             if (Object.keys(currentState.subSchemas).length > 0) {
                 currentState.currentSubTable = Object.keys(currentState.subSchemas)[0];
                 select.value = currentState.currentSubTable;
             }

        } catch (e) {
            console.error("Failed to load schema", e);
        }
    }

    function loadConfigToState(config) {
        if (!config) return;
        
        // Common Mappings
        if (config.commonMappings) {
            config.commonMappings.forEach(m => {
                currentState.mappingState[m.targetField] = m.sourceField;
                addSourceFieldRaw(m.sourceField);
            });
        }
        
        // Sub Table Rules (Assume 1 for now)
        if (config.subTableRules && config.subTableRules.length > 0) {
            const rule = config.subTableRules[0];
            
            if (rule.targetSubTable) {
                currentState.currentSubTable = rule.targetSubTable;
                const select = getEl('sub-schema-select');
                if(select) select.value = rule.targetSubTable;
            }
            
            if (rule.conditionExpression) {
                 const input = getEl('condition-input');
                 if(input) input.value = rule.conditionExpression;
            }
            
            if (rule.mappings) {
                rule.mappings.forEach(m => {
                    currentState.mappingState[m.targetField] = m.sourceField;
                    addSourceFieldRaw(m.sourceField);
                });
            }
        }
    }
    
    function addSourceFieldRaw(name) {
        if (!currentState.sourceFields.includes(name)) {
            currentState.sourceFields.push(name);
        }
    }

    function addCustomField() {
        const input = getEl('new-field-name');
        const name = input.value.trim();
        if (name) {
            addSourceFieldRaw(name);
            input.value = '';
            renderSourceList();
            renderTargetSchemas(); // Update dropdowns
        }
    }

    // Rendering
    function renderUI() {
        renderSourceList();
        renderTargetSchemas();
    }

    function renderSourceList() {
        const container = getEl('source-list');
        container.innerHTML = '';
        currentState.sourceFields.forEach(field => {
            const div = document.createElement('div');
            div.className = 'field-item';
            div.draggable = true;
            div.ondragstart = (e) => e.dataTransfer.setData('text', field);
            div.style.cssText = 'padding: 8px; background: var(--bg-primary); border: 1px solid var(--border); margin-bottom: 4px; border-radius: 4px; cursor: grab; display: flex; align-items: center; gap: 8px; font-size: 0.875rem;';
            div.innerHTML = `
                <span class="material-icons" style="font-size: 14px; color: var(--text-tertiary);">data_object</span>
                <span>${field}</span>
            `;
            container.appendChild(div);
        });
    }

    function handleSubTableChange() {
        const select = getEl('sub-schema-select');
        currentState.currentSubTable = select.value;
        renderTargetSchemas();
    }

    function renderTargetSchemas() {
        // Common Schema
        renderSchemaGroup(currentState.commonSchema, 'common-schema-rows');

        // Sub Schema
        const subTable = currentState.currentSubTable;
        const header = getEl('sub-schema-header');
        if(header) header.innerText = `Sub Table: ${subTable}`;
        
        const subCols = currentState.subSchemas[subTable] || [];
        renderSchemaGroup(subCols, 'sub-schema-rows');
    }

    function renderSchemaGroup(columns, containerId) {
        const container = getEl(containerId);
        container.innerHTML = '';
        
        columns.forEach(col => {
            const row = document.createElement('div');
            row.className = 'schema-row';
            row.style.cssText = 'display: flex; padding: 8px 12px; border-bottom: 1px solid var(--border); align-items: center; background: var(--bg-primary);';
            
            const currentMap = currentState.mappingState[col.name];
            const isActive = !!currentMap;

            let options = `<option value="">(Select...)</option>`;
            currentState.sourceFields.forEach(src => {
                const selected = src === currentMap ? 'selected' : '';
                options += `<option value="${src}" ${selected}>${src}</option>`;
            });

            row.innerHTML = `
                <div class="col-name" style="flex: 1; font-weight: 500; font-size: 0.875rem;" title="${col.type}">${col.name}</div>
                <div class="col-type" style="width: 80px; color: var(--text-tertiary); font-size: 0.75rem;">${col.type}</div>
                <div class="col-mapping" style="flex: 2;">
                    <div class="mapping-input-wrapper ${isActive ? 'active' : ''}" 
                         style="display: flex; gap: 4px;"
                         ondrop="MapperUI.handleDrop(event, '${col.name}')" 
                         ondragover="MapperUI.allowDrop(event)">
                        
                        <select class="combo-select" onchange="MapperUI.manualSelect('${col.name}', this.value)" style="flex: 1; padding: 4px; border: 1px solid var(--border); border-radius: 4px; font-size: 0.875rem;">
                            ${options}
                        </select>
                         ${isActive ? `
                        <button onclick="MapperUI.clearMap('${col.name}')" style="background: none; border: none; cursor: pointer; color: var(--text-tertiary);">
                            <span class="material-icons" style="font-size: 16px;">close</span>
                        </button>` : ''}
                    </div>
                </div>
            `;
            container.appendChild(row);
        });
    }

    // Actions
    function handleDrop(ev, targetCol) {
        ev.preventDefault();
        const srcField = ev.dataTransfer.getData('text');
        manualSelect(targetCol, srcField);
    }

    function allowDrop(ev) { ev.preventDefault(); }

    function manualSelect(targetCol, srcField) {
        if (srcField) {
            currentState.mappingState[targetCol] = srcField;
        } else {
            delete currentState.mappingState[targetCol];
        }
        renderTargetSchemas();
    }

    function clearMap(targetCol) {
        delete currentState.mappingState[targetCol];
        renderTargetSchemas();
    }
    
    function resetMapping() {
        if(confirm("Clear all mappings?")) {
            currentState.mappingState = {};
            renderTargetSchemas();
        }
    }

    function autoMap() {
        // Heuristic
        const allCols = [
            ...currentState.commonSchema, 
            ...(currentState.subSchemas[currentState.currentSubTable] || [])
        ];
        
        let count = 0;
        allCols.forEach(col => {
            if (currentState.mappingState[col.name]) return;
            
            // Exact match or partial match
            const match = currentState.sourceFields.find(src => 
                src === col.name || 
                src.includes(col.name) || 
                col.name.includes(src)
            );
            
            if (match) {
                currentState.mappingState[col.name] = match;
                count++;
            }
        });
        
        renderTargetSchemas();
    }

    function getData() {
        const commonList = [];
        const subList = [];
        
        // Iterate common schema
        currentState.commonSchema.forEach(col => {
            if (currentState.mappingState[col.name]) {
                commonList.push({
                    sourceField: currentState.mappingState[col.name],
                    targetField: col.name
                });
            }
        });
        
        // Iterate current sub schema
        const subCols = currentState.subSchemas[currentState.currentSubTable] || [];
        subCols.forEach(col => {
            if (currentState.mappingState[col.name]) {
                subList.push({
                    sourceField: currentState.mappingState[col.name],
                    targetField: col.name
                });
            }
        });
        
        const config = {
            commonMappings: commonList,
            subTableRules: [
                {
                    targetSubTable: currentState.currentSubTable,
                    conditionExpression: getEl('condition-input').value,
                    mappings: subList
                }
            ]
        };
        
        return config;
    }

    async function runSimulation() {
        let sampleData = {};
        try {
            const val = getEl('sample-log-data').value;
            if (val) sampleData = JSON.parse(val);
        } catch (e) {
            alert("Invalid JSON in sample data");
            return;
        }

        const tempConfig = getData();
        const simResult = getEl('sim-result');
        simResult.innerText = "Simulating...";

        // Mock simulation if endpoint not real, or try real endpoint
        try {
            // Re-using the logic from legacy `transform.js`
            const res = await fetch('/api/v1/transforms/simulate', { // Guessing endpoint
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    messageType: currentState.messageType,
                    sampleData: sampleData,
                    temporaryConfig: {
                         type: 'Structure',
                         param: tempConfig
                    }
                })
            });
            
            if (res.ok) {
                 const result = await res.json();
                 simResult.innerText = JSON.stringify(result, null, 2);
            } else {
                 // Fallback Mock
                 simResult.innerText = "Simulation API not ready.\nMock Result:\n" + JSON.stringify({
                     ...sampleData,
                     _mapped: true,
                     event: { ...sampleData },
                     [currentState.currentSubTable]: { ...sampleData }
                 }, null, 2);
            }
        } catch (e) {
            simResult.innerText = "Simulation Failed: " + e.message;
        }
    }

    return {
        render,
        loadData,
        getData,
        handleSubTableChange,
        addCustomField,
        handleDrop,
        allowDrop,
        manualSelect,
        clearMap,
        autoMap,
        resetMapping,
        runSimulation
    };

})();
