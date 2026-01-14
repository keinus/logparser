// Mapper UI Module
window.MapperUI = (function() {
    
    // State (Logic preserved)
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

    // Helper to get element by ID
    function getEl(id) {
        return document.getElementById(id);
    }

    // --- Redesigned Render Function ---
    function render(container) {
        containerEl = container;
        containerEl.innerHTML = `
            <div class="flex flex-col h-full gap-4 text-slate-300">
                
                <!-- 1. Top Controls: Rules & Condition -->
                <div class="grid grid-cols-1 md:grid-cols-12 gap-4">
                    <div class="md:col-span-8">
                        <label class="label py-1">
                            <span class="label-text text-slate-400 text-xs font-mono uppercase">Condition Rule (SpEL)</span>
                        </label>
                        <input type="text" id="condition-input" class="input input-bordered input-sm w-full bg-slate-800 border-slate-700 focus:border-blue-500 font-mono text-sm" placeholder="e.g. dst_port == 80 || protocol == 'HTTP'">
                    </div>
                    <div class="md:col-span-4">
                        <label class="label py-1">
                            <span class="label-text text-slate-400 text-xs font-mono uppercase">Target Sub-Table</span>
                        </label>
                        <select id="sub-schema-select" class="select select-bordered select-sm w-full bg-slate-800 border-slate-700 text-slate-300" onchange="MapperUI.handleSubTableChange()">
                            <!-- Dynamic options -->
                        </select>
                    </div>
                </div>

                <!-- 2. Main Workspace: Source vs Target -->
                <div class="flex flex-col md:flex-row gap-6 flex-1 min-h-0">
                    
                    <!-- Source Panel (Left) -->
                    <div class="w-full md:w-1/3 flex flex-col bg-slate-900 rounded-lg border border-slate-800 shadow-sm overflow-hidden">
                        <div class="p-3 bg-slate-800/50 border-b border-slate-700 flex justify-between items-center">
                            <span class="font-semibold text-sm text-slate-200 flex items-center gap-2">
                                <span class="material-icons-round text-base text-blue-500">data_object</span>
                                Source Fields
                            </span>
                            <span class="text-xs text-slate-500">Drag to map</span>
                        </div>
                        
                        <!-- Add Field Input -->
                        <div class="p-2 border-b border-slate-800 bg-slate-900">
                            <div class="join w-full">
                                <input type="text" id="new-field-name" class="input input-xs input-bordered join-item w-full bg-slate-800 border-slate-700" placeholder="Add custom field...">
                                <button class="btn btn-xs btn-primary join-item" onclick="MapperUI.addCustomField()">
                                    <span class="material-icons-round text-xs">add</span>
                                </button>
                            </div>
                        </div>

                        <!-- Draggable Source List -->
                        <div id="source-list" class="flex-1 overflow-y-auto p-3 space-y-2 scroll-smooth">
                            <!-- Populated by JS -->
                        </div>
                    </div>

                    <!-- Target Panel (Right) -->
                    <div class="w-full md:w-2/3 flex flex-col bg-slate-900 rounded-lg border border-slate-800 shadow-sm overflow-hidden">
                        <div class="p-3 bg-slate-800/50 border-b border-slate-700 flex justify-between items-center">
                            <span class="font-semibold text-sm text-slate-200 flex items-center gap-2">
                                <span class="material-icons-round text-base text-emerald-500">table_chart</span>
                                Target Schema
                            </span>
                            <div class="flex gap-2">
                                <button class="btn btn-xs btn-ghost text-slate-400 hover:text-white" onclick="MapperUI.resetMapping()">
                                    Reset
                                </button>
                                <button class="btn btn-xs btn-outline btn-primary gap-1" onclick="MapperUI.autoMap()">
                                    <span class="material-icons-round text-xs">auto_fix_high</span>
                                    Auto Map
                                </button>
                            </div>
                        </div>

                        <div id="target-area" class="flex-1 overflow-y-auto p-4 space-y-6">
                            <!-- Common Schema Section -->
                            <div class="space-y-2">
                                <div class="flex items-center gap-2 text-xs font-bold text-slate-500 uppercase tracking-wider px-1">
                                    <span class="w-2 h-2 rounded-full bg-indigo-500"></span> Common Table (event)
                                </div>
                                <div id="common-schema-rows" class="border border-slate-800 rounded-md divide-y divide-slate-800 bg-slate-900/50"></div>
                            </div>

                            <!-- Sub Schema Section -->
                            <div class="space-y-2">
                                <div class="flex items-center gap-2 text-xs font-bold text-slate-500 uppercase tracking-wider px-1">
                                    <span class="w-2 h-2 rounded-full bg-pink-500"></span> <span id="sub-schema-header">Sub Table</span>
                                </div>
                                <div id="sub-schema-rows" class="border border-slate-800 rounded-md divide-y divide-slate-800 bg-slate-900/50"></div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 3. Simulation (Collapsible) -->
                <div class="collapse collapse-arrow bg-slate-900 border border-slate-800 rounded-lg">
                    <input type="checkbox" /> 
                    <div class="collapse-title text-sm font-medium flex items-center gap-2 text-slate-300">
                        <span class="material-icons-round text-blue-400">play_circle</span>
                        Simulation Preview
                    </div>
                    <div class="collapse-content">
                        <div class="flex flex-col gap-3 pt-2">
                            <div class="form-control">
                                <label class="label py-1"><span class="label-text-alt text-slate-500">Sample JSON Data</span></label>
                                <textarea id="sample-log-data" class="textarea textarea-bordered textarea-sm bg-slate-800 font-mono text-xs h-20 leading-relaxed text-slate-300 border-slate-700" placeholder='{"src_ip": "192.168.1.1", "method": "GET"}'></textarea>
                            </div>
                            <button class="btn btn-sm btn-primary w-full sm:w-auto self-start" onclick="MapperUI.runSimulation()">
                                Run Simulation
                            </button>
                            <div class="mockup-code bg-slate-950 text-xs p-0 border border-slate-800 min-h-[6rem]">
                                <pre id="sim-result" class="text-emerald-400 p-4"> // Result will appear here...</pre>
                            </div>
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
            }
        } catch (e) {
            console.error("Error initializing mapper:", e);
        }
        
        renderUI();
    }

    async function loadSchemaMetadata() {
        // Mocking Schema Data logic (preserved)
        try {
             // Simulating metadata found in LogParser.
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

    // --- Redesigned Source List ---
    function renderSourceList() {
        const container = getEl('source-list');
        container.innerHTML = '';
        currentState.sourceFields.forEach(field => {
            const chip = document.createElement('div');
            // DaisyUI Badge style
            chip.className = 'badge badge-neutral gap-2 cursor-grab hover:bg-slate-700 hover:text-white transition-colors py-3 w-full justify-start border-slate-700 text-slate-300';
            chip.draggable = true;
            chip.ondragstart = (e) => {
                e.dataTransfer.setData('text', field);
                e.currentTarget.classList.add('opacity-50');
            };
            chip.ondragend = (e) => {
                e.currentTarget.classList.remove('opacity-50');
            };
            
            chip.innerHTML = `
                <span class="material-icons-round text-xs text-slate-500">drag_indicator</span>
                <span class="font-mono text-xs">${field}</span>
            `;
            container.appendChild(chip);
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

    // --- Redesigned Schema Row ---
    function renderSchemaGroup(columns, containerId) {
        const container = getEl(containerId);
        container.innerHTML = '';
        
        columns.forEach(col => {
            const row = document.createElement('div');
            // Table-row like Flex layout
            row.className = 'flex items-center p-2 text-sm gap-4 hover:bg-slate-800 transition-colors group';
            
            const currentMap = currentState.mappingState[col.name];
            const isActive = !!currentMap;

            let options = `<option value="">Select...</option>`;
            currentState.sourceFields.forEach(src => {
                const selected = src === currentMap ? 'selected' : '';
                options += `<option value="${src}" ${selected}>${src}</option>`;
            });

            // Visual Drop Zone Class
            const dropZoneClass = isActive 
                ? 'bg-blue-500/10 border-blue-500/30' 
                : 'bg-slate-950 border-slate-700 border-dashed hover:border-slate-500';

            row.innerHTML = `
                <!-- Column Info -->
                <div class="flex-1 min-w-0">
                    <div class="font-medium text-slate-300 truncate" title="${col.name}">${col.name}</div>
                    <div class="text-[10px] font-mono text-slate-500">${col.type}</div>
                </div>

                <!-- Connector Icon -->
                <div class="text-slate-600">
                    <span class="material-icons-round text-sm">arrow_right_alt</span>
                </div>

                <!-- Mapping Control (Drop Zone) -->
                <div class="flex-[1.5]">
                    <div class="relative flex items-center gap-2 p-1 rounded border ${dropZoneClass} transition-all"
                         ondrop="MapperUI.handleDrop(event, '${col.name}')" 
                         ondragover="MapperUI.allowDrop(event)"
                         ondragenter="this.classList.add('border-blue-500', 'bg-blue-500/5')"
                         ondragleave="this.classList.remove('border-blue-500', 'bg-blue-500/5')">
                        
                        ${isActive ? `
                            <span class="badge badge-sm badge-info gap-1 pl-1 pr-2">
                                <span class="material-icons-round text-[10px]">data_object</span>
                                ${currentMap}
                            </span>
                            <button onclick="MapperUI.clearMap('${col.name}')" class="btn btn-ghost btn-xs btn-circle text-slate-500 hover:text-white absolute right-1">
                                <span class="material-icons-round text-xs">close</span>
                            </button>
                        ` : `
                            <select class="select select-ghost select-xs w-full text-slate-400 font-normal focus:bg-transparent pl-1" 
                                    onchange="MapperUI.manualSelect('${col.name}', this.value)">
                                ${options}
                            </select>
                        `}
                    </div>
                </div>
            `;
            container.appendChild(row);
        });
    }

    // Actions (Preserved Logic)
    function handleDrop(ev, targetCol) {
        ev.preventDefault();
        // Remove highlight class if added via dragenter
        if(ev.currentTarget) {
             ev.currentTarget.classList.remove('border-blue-500', 'bg-blue-500/5');
        }
        
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

        try {
            const res = await fetch('/api/v1/structure/simulate', {
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