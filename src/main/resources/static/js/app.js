// Global State
let currentTab = 'dashboard';
let currentAdapterType = 'input';
let currentEditingId = null;
let adapterTypes = {};
let refreshInterval = null;

// Initialize App
document.addEventListener('DOMContentLoaded', async () => {
    await initializeApp();
    startAutoRefresh();
});

async function initializeApp() {
    await loadMetadata();
    await refreshPipelineStatus();
    await loadDashboard();
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
        const tabs = ['dashboard', 'input', 'parser', 'transform', 'output', 'monitor'];
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
        case 'monitor':
            loadMonitoringData();
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
        setTimeout(loadDashboard, 2000);
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
        setTimeout(loadDashboard, 2000);
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
        setTimeout(loadDashboard, 3000);
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
        renderAdapterList(data.content || [], 'inputList', 'input');
    } catch (error) {
        showToast('Failed to load input adapters: ' + error.message, 'error');
    }
}

async function loadParsers() {
    try {
        const data = await parserAPI.getAll();
        renderAdapterList(data.content || [], 'parserList', 'parser');
    } catch (error) {
        showToast('Failed to load parsers: ' + error.message, 'error');
    }
}

async function loadTransforms() {
    try {
        const data = await transformAPI.getAll();
        renderAdapterList(data.content || [], 'transformList', 'transform');
    } catch (error) {
        showToast('Failed to load transforms: ' + error.message, 'error');
    }
}

async function loadOutputAdapters() {
    try {
        const data = await outputAdapterAPI.getAll();
        renderAdapterList(data.content || [], 'outputList', 'output');
    } catch (error) {
        showToast('Failed to load output adapters: ' + error.message, 'error');
    }
}

// Render Adapter List
function renderAdapterList(adapters, containerId, type) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (adapters.length === 0) {
        container.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-tertiary); padding: 4rem;">No adapters configured</div>';
        return;
    }

    container.innerHTML = adapters.map(adapter => `
        <div class="adapter-card">
            <div class="adapter-header">
                <div class="adapter-info">
                    <h3>
                        <span class="material-icons" style="font-size: 1.25rem; color: var(--primary);">${getAdapterIcon(type)}</span>
                        ${adapter.type}
                        <span class="status-badge ${adapter.enabled ? 'enabled' : 'disabled'}">
                            ${adapter.enabled ? 'Enabled' : 'Disabled'}
                        </span>
                    </h3>
                    <div class="adapter-messagetype">
                        <span class="material-icons" style="font-size: 14px;">label</span>
                        ${adapter.messagetype}
                    </div>
                </div>
            </div>
            
            <div class="adapter-details">
                ${renderAdapterDetails(adapter, type)}
            </div>

            <div class="adapter-footer">
                ${adapter.enabled
                    ? `<button class="btn btn-secondary btn-small" onclick="toggleAdapter('${type}', ${adapter.id}, false)">
                        Disable
                       </button>`
                    : `<button class="btn btn-success btn-small" onclick="toggleAdapter('${type}', ${adapter.id}, true)">
                        Enable
                       </button>`
                }
                <button class="btn btn-primary btn-small" onclick="editAdapter('${type}', ${adapter.id})">
                    Edit
                </button>
                <button class="btn btn-danger btn-small" onclick="deleteAdapter('${type}', ${adapter.id})">
                    Delete
                </button>
            </div>
        </div>
    `).join('');
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

// Structured Render Details
function renderAdapterDetails(adapter, type) {
    let detailsHtml = '';

    // Network Config (TCP/UDP/HTTP)
    if (adapter.port || adapter.host || adapter.url) {
        detailsHtml += '<div class="detail-group"><h4>Network</h4><div class="detail-grid">';
        if (adapter.host) detailsHtml += createDetailItem('Host', adapter.host);
        if (adapter.port) detailsHtml += createDetailItem('Port', adapter.port);
        if (adapter.url) detailsHtml += createDetailItem('URL', adapter.url);
        if (adapter.method) detailsHtml += createDetailItem('Method', adapter.method);
        if (adapter.timeoutMs) detailsHtml += createDetailItem('Timeout', adapter.timeoutMs + ' ms');
        detailsHtml += '</div></div>';
    }

    // Kafka Config
    if (adapter.bootstrapservers || adapter.topicid) {
        detailsHtml += '<div class="detail-group"><h4>Kafka</h4><div class="detail-grid">';
        if (adapter.bootstrapservers) detailsHtml += createDetailItem('Bootstrap Servers', adapter.bootstrapservers);
        if (adapter.topicid) detailsHtml += createDetailItem('Topic ID', adapter.topicid);
        if (adapter.groupId) detailsHtml += createDetailItem('Group ID', adapter.groupId);
        detailsHtml += '</div></div>';
    }

    // File Config
    if (adapter.path) {
        detailsHtml += '<div class="detail-group"><h4>File</h4><div class="detail-grid">';
        detailsHtml += createDetailItem('Path', adapter.path);
        if (adapter.pathPattern) detailsHtml += createDetailItem('Pattern', adapter.pathPattern);
        detailsHtml += createDetailItem('Read From Beginning', adapter.isFromBeginning ? 'Yes' : 'No');
        detailsHtml += '</div></div>';
    }

    // Parser Params (Grok/Regex)
    if (adapter.param && typeof adapter.param === 'string') {
        detailsHtml += '<div class="detail-group"><h4>Pattern</h4><div class="detail-grid full-width">';
        detailsHtml += createDetailItem('Param', adapter.param);
        if (adapter.priority !== undefined) detailsHtml += createDetailItem('Priority', adapter.priority);
        detailsHtml += '</div></div>';
    }

    // Transform Params (Object)
    if (adapter.param && typeof adapter.param === 'object') {
        detailsHtml += '<div class="detail-group"><h4>Rules</h4><div class="detail-grid full-width">';
        // Render nested objects nicely
        let rulesHtml = '';
        if (adapter.param.pass) rulesHtml += createDetailItem('Pass Filter', JSON.stringify(adapter.param.pass, null, 2));
        if (adapter.param.drop) rulesHtml += createDetailItem('Drop Filter', JSON.stringify(adapter.param.drop, null, 2));
        if (adapter.param.add) rulesHtml += createDetailItem('Add Props', JSON.stringify(adapter.param.add, null, 2));
        if (adapter.param.remove) rulesHtml += createDetailItem('Remove Props', JSON.stringify(adapter.param.remove, null, 2));
        
        detailsHtml += rulesHtml || '<span class="detail-value">Custom Rules</span>';
        detailsHtml += '</div></div>';
    }

    // OpenSearch/ES
    if (adapter.index || adapter.osUsername) {
         detailsHtml += '<div class="detail-group"><h4>OpenSearch</h4><div class="detail-grid">';
         if (adapter.index) detailsHtml += createDetailItem('Index', adapter.index);
         if (adapter.action) detailsHtml += createDetailItem('Action', adapter.action);
         detailsHtml += '</div></div>';
    }

    // Fallback for any other fields
    // Exclude common or already handled fields
    const handledFields = [
        'id', 'type', 'messagetype', 'enabled', 'createdAt', 'updatedAt', 'version',
        'host', 'port', 'url', 'method', 'timeoutMs',
        'bootstrapservers', 'topicid', 'groupId',
        'path', 'pathPattern', 'isFromBeginning',
        'param', 'priority',
        'index', 'action', 'osUsername', 'osPassword', // passwords hidden
        'rmqUsername', 'rmqPassword'
    ];

    let otherHtml = '';
    Object.entries(adapter).forEach(([key, value]) => {
        if (!handledFields.includes(key) && value != null && value !== '') {
            otherHtml += createDetailItem(formatFieldName(key), typeof value === 'object' ? JSON.stringify(value) : value);
        }
    });

    if (otherHtml) {
        detailsHtml += `<div class="detail-group"><h4>Other Settings</h4><div class="detail-grid">${otherHtml}</div></div>`;
    }

    return detailsHtml || '<p style="color: var(--text-tertiary);">No additional configuration</p>';
}

function createDetailItem(label, value) {
    // If value is a long JSON string, put it in a pre block or truncate
    let displayValue = value;
    if (typeof value === 'string' && (value.startsWith('{') || value.startsWith('['))) {
        displayValue = `<pre style="margin:0; white-space:pre-wrap; font-family:monospace; font-size:0.75rem;">${value}</pre>`;
    }

    return `
        <div class="detail-item">
            <span class="detail-label">${label}</span>
            <span class="detail-value">${displayValue}</span>
        </div>
    `;
}

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

        // Populate form
        document.getElementById('messageType').value = adapter.messagetype;
        document.getElementById('enabled').checked = adapter.enabled;

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
    document.getElementById('configModal').classList.remove('active');
    currentEditingId = null;
}

// Load Adapter Schema
async function loadAdapterSchema() {
    const typeSelect = document.getElementById('adapterType');
    const selectedType = typeSelect.value;
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

    container.innerHTML = '<h4>Configuration</h4>' + schema.fields.map(field => {
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
}

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
        messagetype: document.getElementById('messageType').value,
        enabled: document.getElementById('enabled').checked
    };

    // Prepare param object for transforms
    if (currentAdapterType === 'transform') {
        data.param = {};
    }

    // Add dynamic fields
    formData.forEach((value, key) => {
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
        showTab(type);
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
        showTab(type);
    } catch (error) {
        showToast('Failed to toggle adapter: ' + error.message, 'error');
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
                        <th>State</th>
                        <th style="width: 60px; text-align: center;">Action</th>
                    </tr>
                </thead>
                <tbody>
    `;

    html += threads.map(thread => {
        const statusColor = thread.alive ? 'var(--success)' : 'var(--danger)';
        const type = thread.componentType || 'UNKNOWN';
        const icon = typeIcons[type] || 'help_outline';
        
        return `
            <tr onclick="showThreadDetail(${JSON.stringify(thread).replace(/"/g, '&quot;')})" style="cursor: pointer;">
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
                <td><span class="thread-state-badge">${thread.state}</span></td>
                <td style="text-align: center;">
                    <span class="material-icons btn-icon-small">info</span>
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

function showThreadDetail(thread) {
    const modal = document.getElementById('threadDetailModal');
    const content = document.getElementById('threadDetailContent');

    content.innerHTML = `
        <div class="thread-detail">
            <div class="detail-section">
                <h4>Basic Information</h4>
                <div class="detail-grid">
                    <div class="detail-item">
                        <span class="detail-label">Thread Name:</span>
                        <span class="detail-value">${thread.name}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Thread ID:</span>
                        <span class="detail-value">${thread.threadId}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">State:</span>
                        <span class="detail-value">${thread.state}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Status:</span>
                        <span class="detail-value">${thread.alive ? 'Alive' : 'Dead'}</span>
                    </div>
                </div>
            </div>

            ${thread.componentName ? `
                <div class="detail-section">
                    <h4>Component Information</h4>
                    <div class="detail-grid">
                        <div class="detail-item">
                            <span class="detail-label">Type:</span>
                            <span class="detail-value">${thread.componentType}</span>
                        </div>
                        <div class="detail-item">
                            <span class="detail-label">Name:</span>
                            <span class="detail-value">${thread.componentName}</span>
                        </div>
                        ${thread.componentId ? `
                            <div class="detail-item">
                                <span class="detail-label">Component ID:</span>
                                <span class="detail-value">${thread.componentId}</span>
                            </div>
                        ` : ''}
                    </div>
                </div>
            ` : ''}

            ${thread.componentConfig && Object.keys(thread.componentConfig).length > 0 ? `
                <div class="detail-section">
                    <h4>Configuration</h4>
                    <div class="detail-grid">
                        ${Object.entries(thread.componentConfig).map(([key, value]) => `
                            <div class="detail-item">
                                <span class="detail-label">${formatFieldName(key)}:</span>
                                <span class="detail-value">${value}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            ` : ''}

            ${thread.metadata && Object.keys(thread.metadata).length > 0 ? `
                <div class="detail-section">
                    <h4>Metadata</h4>
                    <div class="detail-grid">
                        ${Object.entries(thread.metadata).map(([key, value]) => `
                            <div class="detail-item">
                                <span class="detail-label">${formatFieldName(key)}:</span>
                                <span class="detail-value">${value}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            ` : ''}
        </div>
    `;

    modal.style.display = 'block';
}

function closeThreadDetailModal() {
    const modal = document.getElementById('threadDetailModal');
    modal.style.display = 'none';
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
        if (currentTab === 'monitor') {
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