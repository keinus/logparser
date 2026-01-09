// Main Application Logic
const App = (function() {
    
    // State
    const state = {
        currentView: 'overview',
        currentAdapterType: 'input', // input, parser, transform, output
        refreshInterval: null,
        chartInstance: null,
        chartDataBuffer: [],
        maxChartPoints: 60,
        adapterCache: {
            input: [], parser: [], transform: [], output: []
        },
        editingId: null
    };

    // --- Initialization ---
    async function init() {
        console.log('Initializing LogParser UI...');
        
        // Initial Data Load
        await Promise.all([
            loadDashboardStats(),
            loadMetadata(),
            initChart()
        ]);
        
        // Start Polling
        startPolling();
        
        // Setup Search Debounce if needed (currently using onkeyup directly)
        
        // Mock Live Tail if needed (simulated for now as per prototype request)
        startLiveTailSimulation();
    }
    
    function startPolling() {
        // Poll status every 2 seconds
        setInterval(async () => {
            if (state.currentView === 'overview') {
                await loadDashboardStats();
            }
        }, 2000);
    }

    // --- Navigation ---
    function switchView(viewName) {
        state.currentView = viewName;
        
        // Update Sidebar Active State
        document.querySelectorAll('aside nav a').forEach(el => {
            el.classList.remove('sidebar-active', 'text-blue-500', 'bg-blue-500/10');
            el.classList.add('text-slate-400');
        });

        // Determine which nav ID to highlight
        let navId = 'nav-' + viewName;
        if (viewName === 'inputs') { navId = 'nav-input'; state.currentAdapterType = 'input'; }
        else if (viewName === 'outputs') { navId = 'nav-output'; state.currentAdapterType = 'output'; }
        else if (viewName === 'parser') { navId = 'nav-parser'; state.currentAdapterType = 'parser'; }
        else if (viewName === 'transform') { navId = 'nav-transform'; state.currentAdapterType = 'transform'; }
        
        const activeNav = document.getElementById(navId);
        if (activeNav) {
            activeNav.classList.add('sidebar-active');
            activeNav.classList.remove('text-slate-400');
        }

        // Update Header Title
        const titles = {
            'overview': 'Overview',
            'live-tail': 'Live Data Tail',
            'pipeline-visual': 'Pipeline Visualization',
            'inputs': 'Data Sources',
            'parser': 'Parsers',
            'transform': 'Processing Rules',
            'outputs': 'Destinations',
            'settings': 'System Settings'
        };
        document.getElementById('page-title').textContent = titles[viewName] || 'Dashboard';

        // Hide all views
        document.querySelectorAll('[id^="view-"]').forEach(el => el.classList.add('hidden'));

        // Show target view
        if (['inputs', 'outputs', 'parser', 'transform'].includes(viewName)) {
            document.getElementById('view-list-generic').classList.remove('hidden');
            loadAdapterList(state.currentAdapterType);
        } else {
            const target = document.getElementById('view-' + viewName);
            if (target) target.classList.remove('hidden');
            
            if (viewName === 'overview') {
                initChart(); // Re-render chart if canvas was destroyed/hidden
            } else if (viewName === 'pipeline-visual') {
                 updateTopologyCounts();
            } else if (viewName === 'settings') {
                 loadSettings();
            }
        }
    }

    // --- Dashboard & Monitoring ---
    async function loadDashboardStats() {
        try {
            const status = await pipelineAPI.getStatus();
            const threads = await pipelineAPI.getThreads();
            
            // Text Metrics
            document.getElementById('stat-components').textContent = 
                (status.inputAdapterCount || 0) + (status.parserCount || 0) + 
                (status.transformCount || 0) + (status.outputAdapterCount || 0);
            
            const throughput = status.throughput !== undefined ? parseFloat(status.throughput).toFixed(1) : "0.0";
            document.getElementById('stat-throughput').textContent = `${throughput}/s`;
            
            document.getElementById('stat-queue').textContent = status.queueSize || 0;
            document.getElementById('queue-progress').value = status.queueSize || 0;
            
            document.getElementById('stat-threads').textContent = threads.length || 0;
            
            // Status Pill
            const pill = document.getElementById('status-pill');
            const pillText = document.getElementById('pipeline-status-text');
            pillText.textContent = status.status;
            
            pill.className = `badge badge-outline gap-2 ml-2 ${status.status === 'RUNNING' ? 'badge-success' : 'badge-error'}`;
            
            // Footer Stats
            document.getElementById('threads-count').textContent = `Threads: ${threads.length}`;
            // Mem usage is not in API currently, placeholder
            
            // Update Chart Buffer
            updateChart(throughput);

            // Update Breakdown List
            updateBreakdownList(status);

        } catch (e) {
            console.error("Failed to load dashboard stats", e);
        }
    }
    
    function updateBreakdownList(status) {
        const list = document.getElementById('pipeline-breakdown-list');
        if (!list) return;
        
        const items = [
            { label: 'Inputs', count: status.inputAdapterCount, color: 'emerald' },
            { label: 'Parsers', count: status.parserCount, color: 'emerald' },
            { label: 'Transforms', count: status.transformCount, color: 'emerald' },
            { label: 'Outputs', count: status.outputAdapterCount, color: 'amber' }
        ];
        
        list.innerHTML = items.map(item => `
            <div class="flex items-center justify-between p-3 rounded-lg bg-slate-800/50 border border-slate-700/50">
                <div class="flex items-center">
                    <div class="w-2 h-2 rounded-full bg-${item.color}-500 mr-3"></div>
                    <span class="text-sm font-medium text-slate-300">${item.label}</span>
                </div>
                <span class="text-xs text-slate-400 font-mono">${item.count} Active</span>
            </div>
        `).join('');
    }

    function initChart() {
        const ctx = document.getElementById('trafficChart');
        if (!ctx) return;
        
        if (state.chartInstance) {
            // Already initialized
            return;
        }

        const gradient = ctx.getContext('2d').createLinearGradient(0, 0, 0, 400);
        gradient.addColorStop(0, 'rgba(59, 130, 246, 0.5)'); 
        gradient.addColorStop(1, 'rgba(59, 130, 246, 0.0)');

        state.chartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels: Array(60).fill(''),
                datasets: [{
                    label: 'Events Per Second',
                    data: Array(60).fill(0),
                    borderColor: '#3b82f6',
                    backgroundColor: gradient,
                    borderWidth: 2,
                    fill: true,
                    tension: 0.4,
                    pointRadius: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false, // Performance
                plugins: { legend: { display: false } },
                scales: {
                    x: { display: false },
                    y: { 
                        grid: { color: '#1e293b' },
                        ticks: { color: '#64748b' },
                        beginAtZero: true
                    }
                }
            }
        });
    }

    function updateChart(value) {
        if (!state.chartInstance) return;
        
        const data = state.chartInstance.data.datasets[0].data;
        data.push(value);
        data.shift();
        state.chartInstance.update();
    }
    
    // --- Topology Visual ---
    async function updateTopologyCounts() {
        try {
            const status = await pipelineAPI.getStatus();
            document.getElementById('topo-input-count').textContent = (status.inputAdapterCount || 0) + ' Adapters';
            document.getElementById('topo-parser-count').textContent = (status.parserCount || 0) + ' Parsers';
            document.getElementById('topo-transform-count').textContent = (status.transformCount || 0) + ' Transforms';
            document.getElementById('topo-output-count').textContent = (status.outputAdapterCount || 0) + ' Outputs';
        } catch (e) {}
    }

    // --- Generic List View (CRUD) ---
    async function loadAdapterList(type) {
        try {
            const apiMap = {
                'input': inputAdapterAPI,
                'parser': parserAPI,
                'transform': transformAPI,
                'output': outputAdapterAPI
            };
            
            const response = await apiMap[type].getAll();
            const list = response.content || [];
            state.adapterCache[type] = list; // Cache for search
            
            // Update Badge
            document.getElementById(`badge-${type}`).textContent = list.length;
            
            renderList(list, type);
        } catch (e) {
            showToast("Failed to load list: " + e.message, "error");
        }
    }

    function renderList(items, type) {
        const tbody = document.getElementById('generic-list-body');
        tbody.innerHTML = '';
        
        if (items.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" class="text-center py-8 text-slate-500">No configuration found.</td></tr>`;
            return;
        }

        items.forEach(item => {
            const enabled = (item.enabled !== false); // Default true usually, depends on schema
            // Parsers/Transforms usually don't have 'enabled' field in DTO unless added recently, assume active
            // Actually API supports enable/disable for all.
            
            const statusColor = enabled ? 'text-emerald-500' : 'text-slate-500';
            const statusDot = enabled ? 'bg-emerald-500' : 'bg-slate-500';
            const statusText = enabled ? 'Active' : 'Disabled';

            // Extract key config info for display
            let configSummary = getConfigSummary(item);

            const tr = document.createElement('tr');
            tr.className = 'hover:bg-slate-800/50 transition-colors border-b border-slate-800';
            tr.innerHTML = `
                <td>
                    <div class="flex items-center gap-2">
                        <span class="w-2 h-2 rounded-full ${statusDot}"></span>
                        <span class="capitalize text-slate-300 text-xs">${statusText}</span>
                    </div>
                </td>
                <td>
                    <div class="font-bold text-white">${item.messagetype}</div>
                    <div class="text-xs text-slate-500 font-mono">ID: ${item.id}</div>
                </td>
                <td><span class="badge badge-ghost badge-sm font-mono">${item.type}</span></td>
                <td class="text-slate-400 font-mono text-xs truncate max-w-xs" title="${configSummary}">${configSummary}</td>
                <td class="text-right">
                    ${(type === 'input' || type === 'output') ? `
                    <label class="swap swap-rotate btn btn-ghost btn-xs text-slate-400">
                        <input type="checkbox" ${enabled ? 'checked' : ''} onchange="App.toggleAdapter('${type}', ${item.id}, this.checked)" />
                        <span class="swap-on material-icons-round text-sm">toggle_on</span>
                        <span class="swap-off material-icons-round text-sm">toggle_off</span>
                    </label>
                    ` : ''}
                    <button class="btn btn-ghost btn-xs text-blue-400 hover:text-white" onclick="App.editAdapter('${type}', ${item.id})">
                        <span class="material-icons-round text-sm">edit</span>
                    </button>
                    <button class="btn btn-ghost btn-xs text-rose-400 hover:text-rose-300" onclick="App.deleteAdapter('${type}', ${item.id})">
                        <span class="material-icons-round text-sm">delete</span>
                    </button>
                </td>
            `;
            tbody.appendChild(tr);
        });
    }

    function getConfigSummary(item) {
        if (item.host) return `Host: ${item.host}:${item.port}`;
        if (item.url) return `URL: ${item.url}`;
        if (item.topicid) return `Topic: ${item.topicid}`;
        if (item.param) {
            if (typeof item.param === 'string') return item.param;
            return JSON.stringify(item.param);
        }
        return '-';
    }

    // --- Search ---
    function handleSearch(query) {
        const type = state.currentAdapterType;
        const list = state.adapterCache[type] || [];
        const lower = query.toLowerCase();
        
        const filtered = list.filter(item => 
            (item.messagetype && item.messagetype.toLowerCase().includes(lower)) ||
            (item.type && item.type.toLowerCase().includes(lower)) ||
            (item.id && item.id.toString().includes(lower))
        );
        
        renderList(filtered, type);
    }

    // --- Modals & Forms ---
    async function loadMetadata() {
        // Pre-load types if needed, or load on demand
    }

    async function openCreateModal() {
        state.editingId = null;
        document.getElementById('modal-title').textContent = `Add ${capitalize(state.currentAdapterType)}`;
        document.getElementById('config-form').reset();
        document.getElementById('dynamic-fields').innerHTML = '';
        document.getElementById('config-modal').showModal();
        
        // Populate Types
        const typeSelect = document.getElementById('config-type');
        typeSelect.innerHTML = '<option value="">Loading...</option>';
        
        const typeMap = {
            'input': metadataAPI.getInputAdapterTypes,
            'parser': metadataAPI.getParserTypes,
            'transform': metadataAPI.getTransformTypes,
            'output': metadataAPI.getOutputAdapterTypes
        };
        
        const types = await typeMap[state.currentAdapterType]();
        typeSelect.innerHTML = '<option value="">Select Type</option>' + 
            types.map(t => `<option value="${t.className || t.type}">${t.displayName || t.type}</option>`).join('');

        // Toggle Enabled Switch Visibility (Parsers/Transforms usually always enabled)
        document.getElementById('enabled-group').style.display = 
            (state.currentAdapterType === 'input' || state.currentAdapterType === 'output') ? 'block' : 'none';
    }

    async function editAdapter(type, id) {
        state.editingId = id;
        state.currentAdapterType = type; // Safety sync
        
        try {
            const apiMap = {
                'input': inputAdapterAPI,
                'parser': parserAPI,
                'transform': transformAPI,
                'output': outputAdapterAPI
            };
            
            const data = await apiMap[type].getById(id);
            
            // Open Modal
            await openCreateModal(); // Re-use init logic to load types
            document.getElementById('modal-title').textContent = `Edit ${capitalize(type)}`;
            
            // Fill Basic Fields
            document.getElementById('config-messagetype').value = data.messagetype;
            document.getElementById('config-type').value = data.type;
            if (data.enabled !== undefined) {
                 document.getElementById('config-enabled').checked = data.enabled;
            }

            // Load Schema & Dynamic Fields
            await loadSchema(data.type);
            
            // Fill Dynamic Fields
            // Wait for DOM update
            setTimeout(() => {
                // Special Case: Mapper
                if (type === 'transform' && data.type === 'Structure') {
                    MapperUI.loadData(data.messagetype, data);
                    return;
                }
                
                // General Population
                Object.entries(data).forEach(([key, value]) => {
                     // If it's a param object (Transform generic), flatten or handle?
                     // Usually Transforms have `param` as Map. Parsers have `param` as String.
                     if (key === 'param' && typeof value === 'object' && value !== null) {
                         Object.entries(value).forEach(([k, v]) => {
                             const field = document.querySelector(`[name="${k}"]`);
                             if (field) field.value = v;
                         });
                     } else {
                         const field = document.querySelector(`[name="${key}"]`);
                         if (field) field.value = value;
                     }
                });
            }, 100);
            
        } catch (e) {
            showToast("Failed to load adapter details", "error");
        }
    }

    async function loadSchema(adapterType) {
        if (!adapterType) return;
        
        const container = document.getElementById('dynamic-fields');
        container.innerHTML = '<div class="text-center text-slate-500"><span class="loading loading-dots"></span></div>';
        
        try {
            let schema;
            const type = state.currentAdapterType;
            if (type === 'input') schema = await metadataAPI.getInputAdapterSchema(adapterType);
            else if (type === 'parser') schema = await metadataAPI.getParserSchema(adapterType);
            else if (type === 'transform') schema = await metadataAPI.getTransformSchema(adapterType);
            else if (type === 'output') schema = await metadataAPI.getOutputAdapterSchema(adapterType);
            
            // Special Case: Structure Transform -> Render Mapper UI
            if (type === 'transform' && adapterType === 'Structure') {
                MapperUI.render(container);
                MapperUI.loadData(document.getElementById('config-messagetype').value);
                return;
            }

            // Generic Render
            if (!schema.fields || schema.fields.length === 0) {
                container.innerHTML = '<p class="text-slate-500 text-sm">No additional configuration required.</p>';
                return;
            }

            container.innerHTML = schema.fields.map(field => {
                 let inputHtml = '';
                 const required = field.required ? 'required' : '';
                 
                 // Map Types to HTML
                 if (field.type === 'Boolean') {
                     inputHtml = `
                        <select name="${field.name}" class="select select-bordered bg-slate-800 text-white w-full" ${required}>
                            <option value="true">True</option>
                            <option value="false">False</option>
                        </select>`;
                 } else if (field.choices && field.choices.length > 0) {
                     inputHtml = `
                        <select name="${field.name}" class="select select-bordered bg-slate-800 text-white w-full" ${required}>
                            ${field.choices.map(c => `<option value="${c}">${c}</option>`).join('')}
                        </select>`;
                 } else {
                     inputHtml = `<input type="text" name="${field.name}" class="input input-bordered bg-slate-800 text-white w-full" ${required} />`;
                 }

                 return `
                    <div class="form-control">
                        <label class="label">
                            <span class="label-text text-slate-300 capitalize">${formatLabel(field.name)}</span>
                        </label>
                        ${inputHtml}
                        ${field.description ? `<label class="label"><span class="label-text-alt text-slate-500">${field.description}</span></label>` : ''}
                    </div>
                 `;
            }).join('');
            
        } catch (e) {
            container.innerHTML = '<p class="text-rose-400">Failed to load configuration schema.</p>';
        }
    }

    async function handleConfigSubmit(e) {
        e.preventDefault();
        
        const formData = new FormData(e.target);
        const data = {
            type: document.getElementById('config-type').value,
            messagetype: document.getElementById('config-messagetype').value,
        };
        
        if (state.currentAdapterType === 'input' || state.currentAdapterType === 'output') {
            data.enabled = document.getElementById('config-enabled').checked;
        }

        // Special Case: Mapper
        if (state.currentAdapterType === 'transform' && data.type === 'Structure') {
            data.param = MapperUI.getData();
        } else {
            // Collect dynamic fields
            const dynamicFields = document.querySelectorAll('#dynamic-fields [name]');
            const params = {};
            
            dynamicFields.forEach(field => {
                let val = field.value;
                // Basic type coercion if feasible, or let backend handle strings
                if (val === 'true') val = true;
                if (val === 'false') val = false;
                
                // If Transform/Parser, fields often go into 'param'
                if (state.currentAdapterType === 'transform') {
                    params[field.name] = val;
                } else if (state.currentAdapterType === 'parser' && field.name === 'param') {
                    data.param = val; // Grok/Regex pattern usually top level string in simple implementation, but DTO might expect it.
                } else {
                    data[field.name] = val;
                }
            });

            if (state.currentAdapterType === 'transform') {
                data.param = params;
            }
        }

        try {
            const apiMap = {
                'input': inputAdapterAPI,
                'parser': parserAPI,
                'transform': transformAPI,
                'output': outputAdapterAPI
            };
            
            if (state.editingId) {
                await apiMap[state.currentAdapterType].update(state.editingId, data);
                showToast("Updated successfully", "success");
            } else {
                await apiMap[state.currentAdapterType].create(data);
                showToast("Created successfully", "success");
            }
            
            document.getElementById('config_modal').close();
            loadAdapterList(state.currentAdapterType);
            
        } catch (e) {
            showToast("Operation failed: " + e.message, "error");
        }
    }

    async function deleteAdapter(type, id) {
        if (!confirm("Are you sure you want to delete this configuration?")) return;
        try {
            const apiMap = {
                'input': inputAdapterAPI,
                'parser': parserAPI,
                'transform': transformAPI,
                'output': outputAdapterAPI
            };
            await apiMap[type].delete(id);
            showToast("Deleted successfully", "success");
            loadAdapterList(type);
        } catch (e) {
            showToast("Delete failed", "error");
        }
    }
    
    async function toggleAdapter(type, id, checked) {
        try {
            const apiMap = {
                'input': inputAdapterAPI,
                'output': outputAdapterAPI
            };
            if (!apiMap[type]) return; // Parsers/Transforms might not have generic toggle API in this codebase yet
            
            if (checked) await apiMap[type].enable(id);
            else await apiMap[type].disable(id);
            
            showToast(`Adapter ${checked ? 'enabled' : 'disabled'}`, "success");
        } catch (e) {
            showToast("Toggle failed", "error");
            // Revert UI?
            loadAdapterList(type);
        }
    }

    // --- Control Panel ---
    function openControlModal() {
        document.getElementById('control_modal').showModal();
    }
    
    async function reloadPipeline() {
        showToast("Reloading pipeline...", "info");
        try { await pipelineAPI.reload(); showToast("Reload signal sent", "success"); }
        catch(e) { showToast("Reload failed", "error"); }
    }
    
    async function validateAndReload() {
        showToast("Validating...", "info");
        try { await pipelineAPI.validateAndReload(); showToast("Validation passed & Reloaded", "success"); }
        catch(e) { showToast("Validation failed", "error"); }
    }
    
    async function restartPipeline() {
        if(!confirm("Full restart will drop current connections. Continue?")) return;
        showToast("Restarting...", "warning");
        try { await pipelineAPI.restart(); showToast("Restart signal sent", "success"); }
        catch(e) { showToast("Restart failed", "error"); }
    }

    // --- Settings ---
    async function loadSettings() {
        try {
            const val = await settingsAPI.get('parser_threads');
            if (val) document.getElementById('setting-threads').value = val;
        } catch (e) {}
    }
    
    async function saveSettings() {
        const val = document.getElementById('setting-threads').value;
        try {
            await settingsAPI.update('parser_threads', val, 'INTEGER');
            showToast("Settings saved", "success");
        } catch (e) { showToast("Save failed", "error"); }
    }

    // --- Live Tail (Simulated/Mock) ---
    function startLiveTailSimulation() {
        setInterval(() => {
            const view = document.getElementById('view-live-tail');
            if (view.classList.contains('hidden')) return;
            
            const term = document.getElementById('terminal-window');
            if (term.getAttribute('data-paused') === 'true') return;
            
            // Generate dummy log if no websocket logic present
            const now = new Date().toISOString();
            const methods = ['GET', 'POST', 'PUT', 'DELETE'];
            const ips = ['192.168.1.10', '10.0.0.5', '172.16.0.23'];
            const log = `[${now}] INFO [HttpInput] "src": "${ips[Math.floor(Math.random()*3)]}" "method": "${methods[Math.floor(Math.random()*4)]}" "ua": "Mozilla/5.0"`;
            
            const line = document.createElement('div');
            line.className = 'text-slate-300 hover:bg-slate-800/50 px-1 py-0.5 border-b border-slate-800/30';
            line.textContent = log;
            term.appendChild(line);
            
            if (term.children.length > 50) term.removeChild(term.children[1]);
            term.scrollTop = term.scrollHeight;
        }, 1500);
    }
    
    function togglePauseTail(btn) {
        const term = document.getElementById('terminal-window');
        const isPaused = term.getAttribute('data-paused') === 'true';
        if (isPaused) {
            term.setAttribute('data-paused', 'false');
            btn.textContent = 'Pause';
            btn.classList.remove('btn-warning');
            btn.classList.add('btn-secondary');
        } else {
             term.setAttribute('data-paused', 'true');
             btn.textContent = 'Resume';
             btn.classList.remove('btn-secondary');
             btn.classList.add('btn-warning');
        }
    }

    // --- Helpers ---
    function showToast(msg, type = 'info') {
        const container = document.getElementById('toast-container');
        const alertClass = type === 'success' ? 'alert-success' : (type === 'error' ? 'alert-error' : 'alert-info');
        
        const toast = document.createElement('div');
        toast.className = `alert ${alertClass} text-white shadow-lg mb-2`;
        toast.innerHTML = `<span>${msg}</span>`;
        
        container.appendChild(toast);
        setTimeout(() => {
            toast.remove();
        }, 3000);
    }
    
    function capitalize(s) {
        return s.charAt(0).toUpperCase() + s.slice(1);
    }
    
    function formatLabel(s) {
        return s.replace(/([A-Z])/g, ' $1').trim();
    }

    // Public API
    return {
        init,
        switchView,
        handleSearch,
        openCreateModal,
        openControlModal,
        editAdapter,
        deleteAdapter,
        toggleAdapter,
        handleConfigSubmit,
        loadSchema,
        reloadPipeline,
        validateAndReload,
        restartPipeline,
        saveSettings,
        togglePauseTail
    };

})();

// Initialize on Load
document.addEventListener('DOMContentLoaded', App.init);