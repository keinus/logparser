// Global State
let currentTab = 'dashboard';
let currentAdapterType = 'input';
let currentEditingId = null;
let adapterTypes = {};
let refreshInterval = null;
let adapterData = {
    input: [],
    parser: [],
    transform: [],
    output: []
};

// Initialize App
document.addEventListener('DOMContentLoaded', async () => {
    await initializeApp();
    startAutoRefresh();
});

async function initializeApp() {
    await loadMetadata();
    await refreshPipelineStatus();
    await loadDashboard();
    // Pre-load all data for filtering
    await Promise.all([
        loadInputAdapters(),
        loadParsers(),
        loadTransforms(),
        loadOutputAdapters()
    ]);
    showTab('dashboard');
}

// Load Metadata
async function loadMetadata() {
    try {
        const [inputTypes, parserTypes, transformTypes, outputTypes] = await Promise.all([
            metadataAPI.getInputAdapterTypes(),
            metadataAPI.getParserTypes(),
            metadataAPI.getTransformTypes(),
            metadataAPI.getOutputAdapterTypes()
        ]);

        adapterTypes = {
            input: inputTypes,
            parser: parserTypes,
            transform: transformTypes,
            output: outputTypes
        };
    } catch (error) {
        showToast('Failed to load metadata: ' + error.message, 'error');
    }
}

// Tab Management
function showTab(tabName, event) {
    currentTab = tabName;

    // Update tab buttons
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.classList.remove('active');
    });

    // Find and activate the clicked tab button
    const tabButtons = document.querySelectorAll('.tab-button');
    tabButtons.forEach((btn, index) => {
        const tabs = ['dashboard', 'input', 'parser', 'transform', 'output', 'settings'];
        if (tabs[index] === tabName) {
            btn.classList.add('active');
        }
    });

    // Update tab content
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.remove('active');
    });
    document.getElementById(`${tabName}-tab`)?.classList.add('active');

    // Load tab data
    switch (tabName) {
        case 'dashboard':
            loadDashboard();
            loadMonitoringData();
            break;
        case 'input':
            loadInputAdapters();
            break;
        case 'parser':
            loadParsers();
            break;
        case 'transform':
            loadTransforms();
            break;
        case 'output':
            loadOutputAdapters();
            break;
        case 'settings':
            loadPipelineSettings();
            break;
    }
}

// Dashboard Functions
async function loadDashboard() {
    try {
        const [inputData, parserData, transformData, outputData] = await Promise.all([
            inputAdapterAPI.getAll(),
            parserAPI.getAll(),
            transformAPI.getAll(),
            outputAdapterAPI.getAll()
        ]);

        document.getElementById('inputCount').textContent = inputData.totalElements || 0;
        document.getElementById('parserCount').textContent = parserData.totalElements || 0;
        document.getElementById('transformCount').textContent = transformData.totalElements || 0;
        document.getElementById('outputCount').textContent = outputData.totalElements || 0;

        // Load settings
        await loadPipelineSettings();
    } catch (error) {
        console.error('Failed to load dashboard:', error);
    }
}

// Pipeline Status
async function refreshPipelineStatus() {
    try {
        const status = await pipelineAPI.getStatus();
        const statusValue = document.getElementById('statusValue');
        
        // Map status to visual style
        const statusText = status.status || 'Unknown';
        statusValue.textContent = statusText;
        
        statusValue.className = 'status-value';
        if (statusText === 'RUNNING') statusValue.classList.add('running');
        else if (statusText === 'STOPPED') statusValue.classList.add('stopped');
        else if (statusText === 'RELOADING') statusValue.classList.add('reloading');
        else statusValue.classList.add('error');

    } catch (error) {
        console.error('Failed to refresh pipeline status:', error);
        document.getElementById('statusValue').textContent = 'Error';
    }
}

// Pipeline Control Functions
async function reloadPipeline() {
    if (!confirm('Are you sure you want to reload the pipeline configuration?')) return;

    try {
        showProgress(true);
        const result = await pipelineAPI.reload();
        showToast(result.message || 'Pipeline reloaded successfully', 'success');
        await refreshPipelineStatus();
        setTimeout(() => {
            loadDashboard();
            loadMonitoringData();
        }, 2000);
    } catch (error) {
        showToast('Failed to reload pipeline: ' + error.message, 'error');
    } finally {
        showProgress(false);
    }
}

async function validateAndReload() {
    if (!confirm('Are you sure you want to validate and reload the configuration?')) return;

    try {
        showProgress(true);
        const result = await pipelineAPI.validateAndReload();
        showToast(result.message || 'Configuration validated and reloaded', 'success');
        await refreshPipelineStatus();
        setTimeout(() => {
            loadDashboard();
            loadMonitoringData();
        }, 2000);
    } catch (error) {
        showToast('Failed to validate and reload: ' + error.message, 'error');
    } finally {
        showProgress(false);
    }
}

async function restartPipeline() {
    if (!confirm('Are you sure you want to restart the pipeline? This will stop all processing temporarily.')) return;

    try {
        showProgress(true);
        const result = await pipelineAPI.restart();
        showToast(result.message || 'Pipeline restarted successfully', 'success');
        await refreshPipelineStatus();
        setTimeout(() => {
            loadDashboard();
            loadMonitoringData();
        }, 3000);
    } catch (error) {
        showToast('Failed to restart pipeline: ' + error.message, 'error');
    } finally {
        showProgress(false);
    }
}

// Progress Display
function showProgress(show, message = 'Processing...') {
    const progressDiv = document.getElementById('reloadProgress');
    if (show) {
        progressDiv.style.display = 'block';
        document.getElementById('progressText').textContent = message;
        document.getElementById('progressFill').style.width = '50%';
    } else {
        progressDiv.style.display = 'none';
    }
}

// Load Adapter Lists
async function loadInputAdapters() {
    try {
        const data = await inputAdapterAPI.getAll();
        adapterData.input = data.content || [];
        updateBadge('input', adapterData.input.length);
        renderAdapterList(adapterData.input, 'inputList', 'input');
    } catch (error) {
        showToast('Failed to load input adapters: ' + error.message, 'error');
    }
}

async function loadParsers() {
    try {
        const data = await parserAPI.getAll();
        adapterData.parser = data.content || [];
        updateBadge('parser', adapterData.parser.length);
        renderAdapterList(adapterData.parser, 'parserList', 'parser');
    } catch (error) {
        showToast('Failed to load parsers: ' + error.message, 'error');
    }
}

async function loadTransforms() {
    try {
        const data = await transformAPI.getAll();
        adapterData.transform = data.content || [];
        updateBadge('transform', adapterData.transform.length);
        renderAdapterList(adapterData.transform, 'transformList', 'transform');
    } catch (error) {
        showToast('Failed to load transforms: ' + error.message, 'error');
    }
}

async function loadOutputAdapters() {
    try {
        const data = await outputAdapterAPI.getAll();
        adapterData.output = data.content || [];
        updateBadge('output', adapterData.output.length);
        renderAdapterList(adapterData.output, 'outputList', 'output');
    } catch (error) {
        showToast('Failed to load output adapters: ' + error.message, 'error');
    }
}

function updateBadge(type, count) {
    const badge = document.getElementById(`${type}Badge`);
    if (badge) badge.textContent = count;
}

// Filter Adapters
function filterAdapters(type, query) {
    const term = query.toLowerCase();
    const filtered = adapterData[type].filter(item => 
        (item.messagetype && item.messagetype.toLowerCase().includes(term)) ||
        (item.type && item.type.toLowerCase().includes(term)) ||
        (item.id && item.id.toString().includes(term))
    );
    renderAdapterList(filtered, `${type}List`, type);
}

// Render Adapter List
function renderAdapterList(adapters, containerId, type) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (adapters.length === 0) {
        container.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-tertiary); padding: 4rem;">No adapters found</div>';
        return;
    }

    container.innerHTML = adapters.map(adapter => `
        <div class="adapter-card">
            <div class="adapter-header">
                <div style="display: flex; align-items: center;">
                    <div class="adapter-icon">
                        <span class="material-icons">${getAdapterIcon(type)}</span>
                    </div>
                    <div class="adapter-title">
                        <h3>${adapter.messagetype}</h3>
                        <span class="adapter-subtitle">${adapter.type}</span>
                    </div>
                </div>
                ${(type === 'input' || type === 'output') ? `
                <label class="switch">
                    <input type="checkbox" ${adapter.enabled ? 'checked' : ''} onchange="toggleAdapter('${type}', ${adapter.id}, this.checked)">
                    <span class="slider"></span>
                </label>
                ` : ''}
            </div>
            
            <div class="adapter-body">
                <div class="info-grid">
                    ${renderKeyInfo(adapter)}
                </div>
            </div>

            <div class="adapter-footer">
                <button class="btn btn-secondary btn-small" onclick="editAdapter('${type}', ${adapter.id})">
                    <span class="material-icons" style="font-size: 14px;">edit</span> Edit
                </button>
                <button class="btn btn-danger btn-small" onclick="deleteAdapter('${type}', ${adapter.id})">
                    <span class="material-icons" style="font-size: 14px;">delete</span> Delete
                </button>
            </div>
        </div>
    `).join('');
}

// Render Key Info (Simplified View)
function renderKeyInfo(adapter) {
    let items = [];
    
    // Add specific high-value fields first
    if (adapter.host) items.push(createInfoItem('Host', adapter.host));
    if (adapter.port) items.push(createInfoItem('Port', adapter.port));
    if (adapter.bootstrapservers) items.push(createInfoItem('Brokers', adapter.bootstrapservers));
    if (adapter.topicid) items.push(createInfoItem('Topic', adapter.topicid));
    if (adapter.url) items.push(createInfoItem('URL', adapter.url));
    if (adapter.path) items.push(createInfoItem('Path', adapter.path));
    if (adapter.index) items.push(createInfoItem('Index', adapter.index));
    
    // For parser/transform params
    if (adapter.param) {
         if (typeof adapter.param === 'string') {
             items.push(createInfoItem('Pattern', adapter.param));
         } else if (typeof adapter.param === 'object') {
             items.push(createInfoItem('Rules', Object.keys(adapter.param).join(', ')));
         }
    }

    return items.join('') || '<div style="color: var(--text-tertiary); font-size: 0.875rem;">No detailed settings</div>';
}

function createInfoItem(label, value) {
    return `
        <div class="info-item">
            <span class="info-label">${label}</span>
            <span class="info-value" title="${value}">${value}</span>
        </div>
    `;
}

// Get Icon for Adapter Type
function getAdapterIcon(type) {
    const iconMap = {
        'input': 'input',
        'parser': 'code',
        'transform': 'transform',
        'output': 'output'
    };
    return iconMap[type] || 'settings';
}

// Structured Render Details (Still needed for filtering or other logic?) 
// No, the new card view uses renderKeyInfo. But we still need formatFieldName for form generation
function formatFieldName(name) {
    return name.replace(/([A-Z])/g, ' $1')
        .replace(/^./, str => str.toUpperCase())
        .trim();
}

// Modal Management
function openCreateModal(type) {
    currentAdapterType = type;
    currentEditingId = null;

    const modal = document.getElementById('configModal');
    const title = document.getElementById('modalTitle');
    const typeSelect = document.getElementById('adapterType');

    title.innerHTML = `
        <span class="material-icons">add_circle</span>
        Add ${type.charAt(0).toUpperCase() + type.slice(1)}
    `;

    // Populate type dropdown
    const types = adapterTypes[type] || [];
    typeSelect.innerHTML = '<option value="">Select Type</option>' +
        types.map(t => `<option value="${t.className || t.type}">${t.displayName || t.type}</option>`).join('');

    // Reset form
    document.getElementById('configForm').reset();
    document.getElementById('dynamicFields').innerHTML = '';
    document.getElementById('enabled').checked = true;

    // Hide/Show Enabled field based on type
    const enabledGroup = document.getElementById('enabledFieldGroup');
    if (type === 'parser' || type === 'transform') {
        enabledGroup.style.display = 'none';
    } else {
        enabledGroup.style.display = 'block';
    }

    modal.classList.add('active');
}

async function editAdapter(type, id) {
    currentAdapterType = type;
    currentEditingId = id;

    try {
        const adapter = await getAdapterById(type, id);

        const modal = document.getElementById('configModal');
        const title = document.getElementById('modalTitle');
        title.innerHTML = `
            <span class="material-icons">edit</span>
            Edit ${type.charAt(0).toUpperCase() + type.slice(1)}
        `;

        // Hide/Show Enabled field based on type
        const enabledGroup = document.getElementById('enabledFieldGroup');
        if (type === 'parser' || type === 'transform') {
            enabledGroup.style.display = 'none';
        } else {
            enabledGroup.style.display = 'block';
        }

        // Populate form
        document.getElementById('messageType').value = adapter.messagetype;
        if (type === 'input' || type === 'output') {
             document.getElementById('enabled').checked = adapter.enabled;
        }

        // Populate type dropdown
        const typeSelect = document.getElementById('adapterType');
        const types = adapterTypes[type] || [];
        typeSelect.innerHTML = '<option value="">Select Type</option>' +
            types.map(t => `<option value="${t.className || t.type}" ${(t.className || t.type) === adapter.type ? 'selected' : ''}>${t.displayName || t.type}</option>`).join('');

        // Load schema and populate fields
        await loadAdapterSchema();

        // Populate dynamic fields with existing values
        setTimeout(() => populateFormFields(adapter), 100);

        modal.classList.add('active');
    } catch (error) {
        showToast('Failed to load adapter: ' + error.message, 'error');
    }
}

function populateFormFields(adapter) {
    Object.entries(adapter).forEach(([key, value]) => {
        // Special handling for 'param' object in Transforms
        if (key === 'param' && typeof value === 'object' && value !== null && !Array.isArray(value)) {
             Object.entries(value).forEach(([subKey, subValue]) => {
                 const input = document.querySelector(`[name="${subKey}"]`);
                 if (input) setInputValue(input, subValue);
             });
             return;
        }

        const input = document.querySelector(`[name="${key}"]`);
        if (input && value != null) {
            setInputValue(input, value);
        }
    });
}

function setInputValue(input, value) {
    if (input.type === 'checkbox') {
        input.checked = value;
    } else if (typeof value === 'object') {
        // Pretty print JSON for textareas
        input.value = JSON.stringify(value, null, 2);
    } else {
        input.value = value;
    }
}

function closeModal() {
    const modal = document.getElementById('configModal');
    modal.classList.remove('active');
    modal.classList.remove('modal-xl');
    currentEditingId = null;
}

// Load Adapter Schema
async function loadAdapterSchema() {
    const typeSelect = document.getElementById('adapterType');
    const selectedType = typeSelect.value;
    const modal = document.getElementById('configModal');
    
    // Reset modal width
    modal.classList.remove('modal-xl');

    if (!selectedType) {
        document.getElementById('dynamicFields').innerHTML = '';
        return;
    }

    try {
        let schema;
        switch (currentAdapterType) {
            case 'input':
                schema = await metadataAPI.getInputAdapterSchema(selectedType);
                break;
            case 'parser':
                schema = await metadataAPI.getParserSchema(selectedType);
                break;
            case 'transform':
                schema = await metadataAPI.getTransformSchema(selectedType);
                break;
            case 'output':
                schema = await metadataAPI.getOutputAdapterSchema(selectedType);
                break;
        }

        // Special handling for Structure/Mapping Transform
        if (currentAdapterType === 'transform' && (schema.type === 'Structure' || selectedType === 'Structure')) {
            modal.classList.add('modal-xl');
            
            const container = document.getElementById('dynamicFields');
            // Initialize Mapper
            MapperUI.render(container);
            
            // Load Data
            const messageType = document.getElementById('messageType').value;
            let existingConfig = null;
            if (currentEditingId) {
                // We need the full adapter object. We can fetch it or use cached if available.
                // For simplicity, fetch fresh.
                const adapter = await getAdapterById('transform', currentEditingId);
                existingConfig = adapter;
            }
            
            await MapperUI.loadData(messageType, existingConfig);
            return; // Skip standard field rendering
        }

        renderDynamicFields(schema);
    } catch (error) {
        console.error('Failed to load schema:', error);
        document.getElementById('dynamicFields').innerHTML = '<p>Failed to load configuration fields</p>';
    }
}

// Render Dynamic Fields
function renderDynamicFields(schema) {
    const container = document.getElementById('dynamicFields');
    if (!schema || !schema.fields) {
        container.innerHTML = '<p>No additional configuration required</p>';
        return;
    }

    let html = '<h4>Configuration</h4>';

    html += schema.fields.map(field => {
        const inputType = getInputType(field.type);
        const required = field.required ? 'required' : '';

        return `
            <div class="form-group">
                <label for="${field.name}">${formatFieldName(field.name)}:</label>
                ${inputType === 'select'
                    ? `<select name="${field.name}" ${required}>
                        <option value="">Select...</option>
                        ${(field.choices || []).map(choice => `<option value="${choice}">${choice}</option>`).join('')}
                       </select>`
                    : inputType === 'textarea'
                    ? `<textarea name="${field.name}" ${required}></textarea>`
                    : `<input type="${inputType}" name="${field.name}" ${required}>`
                }
                ${field.description ? `<small>${field.description}</small>` : ''}
            </div>
        `;
    }).join('');

    container.innerHTML = html;
}

// Removed openSchemaMapper since it is now integrated


function getInputType(fieldType) {
    const typeMap = {
        'String': 'text',
        'Integer': 'number',
        'Long': 'number',
        'Boolean': 'checkbox',
        'Map': 'textarea',
        'List': 'textarea'
    };
    return typeMap[fieldType] || 'text';
}

// Form Submission
document.getElementById('configForm').addEventListener('submit', async (e) => {
    e.preventDefault();

    const formData = new FormData(e.target);
    const data = {
        type: document.getElementById('adapterType').value,
        messagetype: document.getElementById('messageType').value
    };

    // Only add enabled field for supported types
    if (currentAdapterType === 'input' || currentAdapterType === 'output') {
        data.enabled = document.getElementById('enabled').checked;
    }

    // Prepare param object for transforms
    if (currentAdapterType === 'transform') {
        const typeSelect = document.getElementById('adapterType');
        if (typeSelect.value === 'Structure') {
             // Get data from MapperUI
             data.param = MapperUI.getData();
        } else {
             data.param = {};
        }
    }

    // Add dynamic fields
    formData.forEach((value, key) => {
        // Skip if MapperUI handled it (Structure)
        if (currentAdapterType === 'transform' && document.getElementById('adapterType').value === 'Structure') {
            return;
        }

        if (key !== 'type' && key !== 'messagetype' && key !== 'enabled') {
            let parsedValue = value;
            // Try to parse JSON for Map/List fields
            if (value.startsWith('{') || value.startsWith('[')) {
                try {
                    parsedValue = JSON.parse(value);
                } catch {
                    parsedValue = value;
                }
            } else if (value === 'true' || value === 'false') {
                // Parse boolean values (from checkboxes or selects)
                parsedValue = value === 'true';
            } else if (!isNaN(value) && value.trim() !== '') {
                 // Keep as string if schema says so, but here we don't know schema
                 // Let's rely on backend parsing/coercion which usually works for basic types
                 // Or if it looks like a number, parse it?
                 // Safer to let backend parse strings if DTO fields are Integer.
                 // But for untyped Maps, we might want numbers.
                 // Let's parse int if it looks like int.
                 if (/^-?\d+$/.test(value)) parsedValue = parseInt(value, 10);
            }

            if (currentAdapterType === 'transform') {
                data.param[key] = parsedValue;
            } else {
                data[key] = parsedValue;
            }
        }
    });

    try {
        if (currentEditingId) {
            await updateAdapter(currentAdapterType, currentEditingId, data);
            showToast('Adapter updated successfully', 'success');
        } else {
            await createAdapter(currentAdapterType, data);
            showToast('Adapter created successfully', 'success');
        }

        closeModal();
        // Refresh the current tab data
        if (currentAdapterType === 'input') await loadInputAdapters();
        else if (currentAdapterType === 'parser') await loadParsers();
        else if (currentAdapterType === 'transform') await loadTransforms();
        else if (currentAdapterType === 'output') await loadOutputAdapters();
        
        showTab(currentAdapterType);
    } catch (error) {
        showToast('Failed to save adapter: ' + error.message, 'error');
    }
});

// CRUD Operations
async function getAdapterById(type, id) {
    switch (type) {
        case 'input': return await inputAdapterAPI.getById(id);
        case 'parser': return await parserAPI.getById(id);
        case 'transform': return await transformAPI.getById(id);
        case 'output': return await outputAdapterAPI.getById(id);
    }
}

async function createAdapter(type, data) {
    switch (type) {
        case 'input': return await inputAdapterAPI.create(data);
        case 'parser': return await parserAPI.create(data);
        case 'transform': return await transformAPI.create(data);
        case 'output': return await outputAdapterAPI.create(data);
    }
}

async function updateAdapter(type, id, data) {
    switch (type) {
        case 'input': return await inputAdapterAPI.update(id, data);
        case 'parser': return await parserAPI.update(id, data);
        case 'transform': return await transformAPI.update(id, data);
        case 'output': return await outputAdapterAPI.update(id, data);
    }
}

async function deleteAdapter(type, id) {
    if (!confirm('Are you sure you want to delete this adapter?')) return;

    try {
        switch (type) {
            case 'input': await inputAdapterAPI.delete(id); break;
            case 'parser': await parserAPI.delete(id); break;
            case 'transform': await transformAPI.delete(id); break;
            case 'output': await outputAdapterAPI.delete(id); break;
        }

        showToast('Adapter deleted successfully', 'success');
        // Refresh and re-filter
        if (type === 'input') await loadInputAdapters();
        else if (type === 'parser') await loadParsers();
        else if (type === 'transform') await loadTransforms();
        else if (type === 'output') await loadOutputAdapters();
        
    } catch (error) {
        showToast('Failed to delete adapter: ' + error.message, 'error');
    }
}

async function toggleAdapter(type, id, enable) {
    try {
        const api = type === 'input' ? inputAdapterAPI :
                    type === 'parser' ? parserAPI :
                    type === 'transform' ? transformAPI : outputAdapterAPI;

        if (enable) {
            await api.enable(id);
        } else {
            await api.disable(id);
        }

        showToast(`Adapter ${enable ? 'enabled' : 'disabled'} successfully`, 'success');
        // No need to reload everything, just update state locally if needed, but simplest to let it be
        // Update local data cache? 
        const item = adapterData[type].find(a => a.id === id);
        if (item) item.enabled = enable;
        
    } catch (error) {
        showToast('Failed to toggle adapter: ' + error.message, 'error');
        // Revert toggle visually? Ideally yes, but simplistic for now
        // reload data to reset switch
        if (type === 'input') await loadInputAdapters();
        else if (type === 'parser') await loadParsers();
        else if (type === 'transform') await loadTransforms();
        else if (type === 'output') await loadOutputAdapters();
    }
}

// Monitoring
async function loadMonitoringData() {
    try {
        const [status, progress, threads] = await Promise.all([
            pipelineAPI.getStatus(),
            pipelineAPI.getReloadProgress(),
            pipelineAPI.getThreads()
        ]);

        // Key Metrics
        const statusEl = document.getElementById('monitorStatus');
        statusEl.textContent = status.status;
        // Reset classes and add specific status class
        statusEl.className = 'metric-value';
        statusEl.classList.add(status.status ? status.status.toLowerCase() : 'unknown');
        
        // Throughput
        const throughput = status.throughput !== undefined ? status.throughput : 0;
        document.getElementById('monitorThroughput').textContent = `${parseFloat(throughput).toFixed(1)}/s`;
        
        document.getElementById('monitorQueueSize').textContent = status.queueSize;
        document.getElementById('monitorThreadCount').textContent = threads.length;

        // Component Status List
        const componentList = document.getElementById('componentStatusList');
        componentList.innerHTML = `
            <div class="status-row">
                <span class="material-icons" style="font-size: 1.2em; color: var(--primary);">input</span>
                <span>Input Adapters</span>
                <strong style="margin-left: auto;">${status.inputAdapterCount}</strong>
            </div>
            <div class="status-row">
                <span class="material-icons" style="font-size: 1.2em; color: var(--warning);">code</span>
                <span>Parsers</span>
                <strong style="margin-left: auto;">${status.parserCount}</strong>
            </div>
            <div class="status-row">
                <span class="material-icons" style="font-size: 1.2em; color: var(--secondary);">transform</span>
                <span>Transforms</span>
                <strong style="margin-left: auto;">${status.transformCount}</strong>
            </div>
            <div class="status-row">
                <span class="material-icons" style="font-size: 1.2em; color: var(--success);">output</span>
                <span>Output Adapters</span>
                <strong style="margin-left: auto;">${status.outputAdapterCount}</strong>
            </div>
        `;

        // Render progress details
        const progressContent = document.getElementById('reloadProgressDetail');
        progressContent.innerHTML = Object.entries(progress).map(([key, value]) => `
            <div class="status-item">
                <div class="status-item-label">${formatFieldName(key)}:</div>
                <div class="status-item-value">${typeof value === 'object' ? JSON.stringify(value) : value}</div>
            </div>
        `).join('');

        // Render threads
        renderThreads(threads);
    } catch (error) {
        console.error('Failed to load monitoring data:', error);
    }
}

function renderThreads(threads) {
    const container = document.getElementById('threadsContainer');

    if (!threads || threads.length === 0) {
        container.innerHTML = '<div class="empty-state">No active threads</div>';
        return;
    }

    // Sort threads by ID
    threads.sort((a, b) => a.threadId - b.threadId);

    const typeIcons = {
        'INPUT': 'input',
        'OUTPUT': 'output',
        'PARSER': 'code',
        'BATCH': 'schedule',
        'MONITOR': 'visibility',
        'UNKNOWN': 'help_outline'
    };
    
    // Create Table
    let html = `
        <div class="table-responsive">
            <table class="thread-table">
                <thead>
                    <tr>
                        <th style="width: 60px; text-align: center;">Status</th>
                        <th style="width: 60px;">ID</th>
                        <th>Name</th>
                        <th>Type</th>
                        <th>Component</th>
                        <th>Comp. ID</th>
                        <th>State</th>
                        <th>Extra Info</th>
                    </tr>
                </thead>
                <tbody>
    `;

    html += threads.map(thread => {
        const statusColor = thread.alive ? 'var(--success)' : 'var(--danger)';
        const type = thread.componentType || 'UNKNOWN';
        const icon = typeIcons[type] || 'help_outline';
        
        // Prepare extra info summary
        let extraInfo = '';
        if (thread.metadata && Object.keys(thread.metadata).length > 0) {
            extraInfo = Object.entries(thread.metadata)
                .map(([k, v]) => `${k}: ${v}`)
                .join(', ');
        } else if (thread.componentConfig && Object.keys(thread.componentConfig).length > 0) {
            // Fallback to config if no metadata
             extraInfo = Object.entries(thread.componentConfig)
                .filter(([k, v]) => typeof v !== 'object')
                .map(([k, v]) => `${k}: ${v}`)
                .join(', ');
        }
        
        return `
            <tr>
                <td style="text-align: center;">
                    <span class="material-icons" style="font-size: 1.2em; color: ${statusColor}; vertical-align: middle;">${thread.alive ? 'check_circle' : 'cancel'}</span>
                </td>
                <td style="font-family: monospace;">${thread.threadId}</td>
                <td class="thread-name-cell" style="font-weight: 500;">${thread.name}</td>
                <td>
                    <span class="thread-type-badge ${type.toLowerCase()}">
                        <span class="material-icons" style="font-size: 14px; margin-right: 4px;">${icon}</span>
                        ${type}
                    </span>
                </td>
                <td>${thread.componentName || '-'}</td>
                <td>${thread.componentId || '-'}</td>
                <td><span class="thread-state-badge">${thread.state}</span></td>
                <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.75rem; color: var(--text-secondary);">
                    ${extraInfo || '-'}
                </td>
            </tr>
        `;
    }).join('');

    html += `
                </tbody>
            </table>
        </div>
    `;

    container.innerHTML = html;
}

// Toast Notification
function showToast(message, type = 'info') {
    const toast = document.getElementById('toast');
    const iconElement = toast.querySelector('.toast-icon');
    const messageElement = toast.querySelector('.toast-message');

    // Set icon based on type
    const iconMap = {
        'success': 'check_circle',
        'error': 'error',
        'warning': 'warning',
        'info': 'info'
    };

    iconElement.textContent = iconMap[type] || 'info';
    messageElement.textContent = message;
    toast.className = `toast ${type} show`;

    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}

// Auto Refresh
function startAutoRefresh() {
    refreshInterval = setInterval(async () => {
        if (currentTab === 'dashboard') {
            await loadMonitoringData();
        }
        await refreshPipelineStatus();
    }, 5000);
}

// Close modal on outside click
window.onclick = function(event) {
    const modal = document.getElementById('configModal');
    if (event.target === modal) {
        closeModal();
    }
}

// Pipeline Settings
async function loadPipelineSettings() {
    try {
        const parserThreads = await settingsAPI.get('parser_threads');
        const input = document.getElementById('parserThreadsInput');
        if (input) {
            input.value = parserThreads || 4; // Default to 4 if null
        }
    } catch (error) {
        console.error('Failed to load pipeline settings:', error);
    }
}

async function savePipelineSettings(event) {
    event.preventDefault();
    const input = document.getElementById('parserThreadsInput');
    const value = parseInt(input.value, 10);

    if (isNaN(value) || value < 1) {
        showToast('Please enter a valid number of threads (minimum 1)', 'error');
        return;
    }

    try {
        await settingsAPI.update('parser_threads', value, 'INTEGER');
        showToast('Settings saved. Restart pipeline to apply changes.', 'success');
    } catch (error) {
        showToast('Failed to save settings: ' + error.message, 'error');
    }
}
