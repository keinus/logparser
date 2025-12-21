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
        statusValue.textContent = status.status || 'Unknown';
        statusValue.className = 'status-value ' + (status.status || '').toLowerCase();
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
        container.innerHTML = '<p style="text-align: center; color: #6b7280; padding: 40px;">No adapters configured</p>';
        return;
    }

    container.innerHTML = adapters.map(adapter => `
        <div class="adapter-card">
            <div class="adapter-header">
                <div class="adapter-info">
                    <h3>
                        <span class="material-icons" style="font-size: 20px; vertical-align: middle; margin-right: 8px;">${getAdapterIcon(type)}</span>
                        <span class="adapter-type">${adapter.type}</span>
                        <span class="status-badge ${adapter.enabled ? 'enabled' : 'disabled'}">
                            <span class="material-icons" style="font-size: 14px; vertical-align: middle;">${adapter.enabled ? 'check_circle' : 'cancel'}</span>
                            ${adapter.enabled ? 'Enabled' : 'Disabled'}
                        </span>
                    </h3>
                    <div class="adapter-messagetype">
                        <span class="material-icons" style="font-size: 16px; vertical-align: middle; margin-right: 4px;">label</span>
                        Message Type: ${adapter.messagetype}
                    </div>
                </div>
                <div class="adapter-actions">
                    ${adapter.enabled
                        ? `<button class="btn btn-secondary btn-small" onclick="toggleAdapter('${type}', ${adapter.id}, false)">
                            <span class="material-icons">toggle_off</span>
                            <span>Disable</span>
                           </button>`
                        : `<button class="btn btn-success btn-small" onclick="toggleAdapter('${type}', ${adapter.id}, true)">
                            <span class="material-icons">toggle_on</span>
                            <span>Enable</span>
                           </button>`
                    }
                    <button class="btn btn-primary btn-small" onclick="editAdapter('${type}', ${adapter.id})">
                        <span class="material-icons">edit</span>
                        <span>Edit</span>
                    </button>
                    <button class="btn btn-danger btn-small" onclick="deleteAdapter('${type}', ${adapter.id})">
                        <span class="material-icons">delete</span>
                        <span>Delete</span>
                    </button>
                </div>
            </div>
            <div class="adapter-details">
                ${renderAdapterDetails(adapter, type)}
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

// Render Adapter Details
function renderAdapterDetails(adapter, type) {
    const excludeFields = ['id', 'type', 'messagetype', 'enabled', 'createdAt', 'updatedAt'];
    const fields = Object.entries(adapter)
        .filter(([key, value]) => !excludeFields.includes(key) && value != null)
        .map(([key, value]) => {
            const displayValue = typeof value === 'object' ? JSON.stringify(value) : value;
            return `
                <div class="adapter-field">
                    <div class="adapter-field-label">${formatFieldName(key)}:</div>
                    <div class="adapter-field-value">${displayValue}</div>
                </div>
            `;
        });

    return fields.length > 0 ? fields.join('') : '<p style="color: #6b7280;">No additional configuration</p>';
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
                 // Attempt to parse numbers, but be careful with strings that look like numbers
                 // For now, let's trust the input type or backend coercion, or explicitly parse if needed.
                 // The backend uses strict types, so sending strings for numbers might work if Jackson handles it.
                 // However, let's keep it as string unless we know it's a number field?
                 // The schema has type info. Ideally we use that.
                 // But here we don't have easy access to schema field type.
                 // Let's rely on JSON.parse for explicit JSON/Arrays, and keep strings otherwise.
                 // Actually, InputAdapterConfig uses Integer for port. "8080" string is fine for Jackson usually.
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

        // Render status details
        const statusContent = document.getElementById('statusDetailContent');
        statusContent.innerHTML = Object.entries(status).map(([key, value]) => `
            <div class="status-item">
                <div class="status-item-label">${formatFieldName(key)}:</div>
                <div class="status-item-value">${typeof value === 'object' ? JSON.stringify(value) : value}</div>
            </div>
        `).join('');

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

    // Group threads by component type
    const threadsByType = threads.reduce((acc, thread) => {
        const type = thread.componentType || 'UNKNOWN';
        if (!acc[type]) {
            acc[type] = [];
        }
        acc[type].push(thread);
        return acc;
    }, {});

    const typeIcons = {
        'INPUT': 'input',
        'OUTPUT': 'output',
        'PARSER': 'transform',
        'BATCH': 'schedule',
        'MONITOR': 'visibility',
        'UNKNOWN': 'help_outline'
    };

    const typeColors = {
        'INPUT': '#2196F3',
        'OUTPUT': '#4CAF50',
        'PARSER': '#FF9800',
        'BATCH': '#9C27B0',
        'MONITOR': '#00BCD4',
        'UNKNOWN': '#757575'
    };

    container.innerHTML = Object.entries(threadsByType).map(([type, threads]) => `
        <div class="thread-group">
            <div class="thread-group-header" style="border-left-color: ${typeColors[type]}">
                <span class="material-icons">${typeIcons[type]}</span>
                <span>${type} (${threads.length})</span>
            </div>
            <div class="thread-list">
                ${threads.map(thread => renderThreadCard(thread, typeColors[type])).join('')}
            </div>
        </div>
    `).join('');
}

function renderThreadCard(thread, color) {
    const statusIcon = thread.alive ? 'check_circle' : 'cancel';
    const statusColor = thread.alive ? '#4CAF50' : '#f44336';

    return `
        <div class="thread-card" onclick="showThreadDetail(${JSON.stringify(thread).replace(/"/g, '&quot;')})">
            <div class="thread-card-header">
                <div class="thread-name">
                    <span class="material-icons" style="color: ${color}">${statusIcon}</span>
                    <span>${thread.name}</span>
                </div>
                <div class="thread-state" style="background-color: ${statusColor}20; color: ${statusColor}">
                    ${thread.state}
                </div>
            </div>
            ${thread.componentName ? `
                <div class="thread-component">
                    <span class="material-icons">label</span>
                    <span>${thread.componentName}</span>
                </div>
            ` : ''}
            <div class="thread-meta">
                <span>ID: ${thread.threadId}</span>
            </div>
        </div>
    `;
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
