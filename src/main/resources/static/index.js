// Global Paper Collections mapped for dropdowns & scaling
const standardPapers = [
    { name: "A4", area: 501135.0, priceInput: "A4", label: "A4 (Standard)" },
    { name: "A3", area: 1002270.0, priceInput: "A3", label: "A3 (Scaled Up - Double Area)" },
    { name: "A5", area: 250567.0, priceInput: "A5", label: "A5 (Scaled Down - Half Area)" },
    { name: "Legal", area: 604800.0, priceInput: "Legal", label: "Legal" }
];

let customPapers = [];
let editingIndex = -1; // Tracks the index of the custom paper currently being edited
let settingsSaved = false; // true once settings exist in the database (decides create vs update on Save)



document.addEventListener('DOMContentLoaded', () => {
    // 1. Auto-load existing user settings on page load
    loadSavedSettings();
    loadIccProfiles();
    loadCustomIccProfiles();
    let ID_for_job = null;

    const printModeSelect = document.getElementById('printMode');
    const customNContainer = document.getElementById('customNContainer');


    if (printModeSelect && customNContainer) {
            printModeSelect.addEventListener('change', function() {
                if (this.value === 'custom') {
                    customNContainer.style.display = 'flex';
                    document.getElementById('customNValue').focus();
                } else {
                    customNContainer.style.display = 'none';
                }


                if (this.value === 'booklet') {
                    const duplexSelect = document.getElementById('duplexMode');
                    if (duplexSelect) {
                        duplexSelect.value = 'duplex';
                    }
                    
                }
            });
        }

    // 2. Event Listener: Color Capability Dropdown Toggle
    document.getElementById('color_capability').addEventListener('change', function() {
        const full_color = document.getElementById('full_color');
        
        if (this.value === 'Monochrome') {
            full_color.style.display = 'none';
            // Clear out color inputs when switching to monochrome
            document.getElementById('cyanPrice').value = '';
            document.getElementById('cyanYield').value = '';
            document.getElementById('magentaPrice').value = '';
            document.getElementById('magentaYield').value = '';
            document.getElementById('yellowPrice').value = '';
            document.getElementById('yellowYield').value = '';
        } else {
            full_color.style.display = 'block';
        }
    });

    // Event Listener: Add / Save Edit Custom Paper Button
    const addCustomPaperBtn = document.getElementById('addCustomPaperBtn');
    addCustomPaperBtn.addEventListener('click', () => {
        const name = document.getElementById('customPaperName').value.trim();
        const areaCm2 = parseFloat(document.getElementById('customPaperArea').value);
        const price = parseFloat(document.getElementById('customPaperPrice').value);

        if (!name || isNaN(areaCm2) || isNaN(price)) {
            alert("Please fill all custom paper fields with valid values.");
            return;
        }
        
        // Convert cm^2 to points^2 for PDFBox processing
        const areaInPoints = areaCm2 * 803.521617; // 1 cm² = 803.521627 points²
        
        if (editingIndex > -1) {
            // Update existing paper
            customPapers[editingIndex] = { name, areaInPoints, price };
            editingIndex = -1; // Reset state
            addCustomPaperBtn.textContent = 'Add';
            addCustomPaperBtn.style.backgroundColor = ''; // Reset to default CSS style
        } else {
            // Add new paper
            customPapers.push({ name, areaInPoints, price });
        }
        
        renderCustomPapers();
        updatePaperDropdown();

        // Clear Fields
        document.getElementById('customPaperName').value = '';
        document.getElementById('customPaperArea').value = '';
        document.getElementById('customPaperPrice').value = '';
    });

    // 3. Event Listener: Save Button (Form Submission)
    document.getElementById('inkCostForm').addEventListener('submit', async function(event) {
        event.preventDefault();
        if (settingsSaved) {
            await sendSettingsRequest('/api/settings/update', 'PUT', 'Settings saved.');
        } else {
            const created = await sendSettingsRequest('/api/settings/create', 'POST', 'Settings saved.');
            if (created) settingsSaved = true;
        }
    });

    // 5. Event Listener: Clear Settings Button
    document.getElementById('clear_settings').addEventListener('click', async function() {
        const resetBtn = this;
        if (!resetBtn.classList.contains('armed')) {
            resetBtn.classList.add('armed');
            resetBtn.textContent = 'Click again to erase everything';
            setTimeout(() => {
                resetBtn.classList.remove('armed');
                resetBtn.textContent = 'Reset all settings';
            }, 4000);
            return;
        }
        resetBtn.classList.remove('armed');
        resetBtn.textContent = 'Reset all settings';

        document.getElementById('inkCostForm').reset();
        document.getElementById('covarage_percentage').value = 5; 
        document.getElementById('color_capability').dispatchEvent(new Event('change'));
        
        customPapers = [];
        editingIndex = -1; // Reset edit state if active
        document.getElementById('addCustomPaperBtn').textContent = 'Add';
        document.getElementById('addCustomPaperBtn').style.backgroundColor = '';
        
        renderCustomPapers();
        updatePaperDropdown();

        try {
            const response = await fetch('/api/settings/clear', {
                method: 'DELETE'
            });

            const message = await response.text();
            const statusEl = document.getElementById('settingsStatus');
            statusEl.innerText = message;
            statusEl.className = response.ok ? 'status-ok' : 'status-error';
            if (response.ok) {
                console.log(message);
                settingsSaved = false;   // nothing saved any more: the next Save creates again
            }
        } catch (error) {
            console.error('Error clearing printer settings:', error);
            alert('A network error occurred while attempting to clear settings.');
        }
    });

    // 6. Event Listener: PDF Upload & Analysis Form
    document.getElementById('uploadForm').addEventListener('submit', async function(event) {
        event.preventDefault(); 
        
        const fileInput = document.getElementById('pdfFileInput');
        const file = fileInput.files[0];
        
        const colorMode = document.getElementById('color_capability').value;

        // Custom Paper Calculation Resolution
        const target = document.getElementById('targetPaperSize').value;
        let targetArea = 501135.0; // Standard Fallback
        let targetCost = 0.0;

        const stdPaper = standardPapers.find(p => p.name === target);
        if (stdPaper) {
            targetArea = stdPaper.area;
            targetCost = parseFloat(document.getElementById(stdPaper.priceInput).value) || 0.0;
        } else {
            const custPaper = customPapers.find(p => p.name === target);
            if (custPaper) {
                targetArea = custPaper.areaInPoints;
                targetCost = custPaper.price;
            }
        }

        let layout = printModeSelect.value;
        let nUpFactor = 1;

        if (layout === 'custom') {
            const customN = parseInt(document.getElementById('customNValue').value, 10);
            nUpFactor = (!isNaN(customN) && customN > 0) ? customN : 1;
            //layout = `${nUpFactor}`;
        } else {
            const match = layout.match(/^(\d+)up$/);
            if (match) {
                nUpFactor = parseInt(match[1], 10);
            }
        }

        console.log("Target Paper: " + target + " | Area: " + targetArea + " | Cost: " + targetCost);

        // Resolve the print layout and N-up factor
        //const layout = document.getElementById('printMode').value;
        const sides = document.getElementById('duplexMode').value;
        const color_page_range = document.getElementById('colorPages').value;
        const monochrome_page_range = document.getElementById('monoPages').value;
        const selectedIccProfile = document.getElementById('iccProfileSelect').value;

        if (!file) {
            document.getElementById('uploadStatus').innerText = 'Please select a PDF file.';
            return;
        }

        const generaterandonJobID = () => {
            const timestamp = Date.now().toString(); // Convert timestamp to base36
            const randompart = Math.floor(Math.random() * 8+2).toString(); // Random number to base36

            return timestamp + randompart;
        };

        //const jobId = crypto.randomUUID(); // Generate a unique job ID for this upload
        const jobID = generaterandonJobID();
        alert("Job ID: " + jobID); // For debugging purposes, can be removed in production

        ID_for_job = jobID; // Store the job ID for potential future use
        const statusText = document.getElementById('uploadStatus');
        statusText.innerText = 'Connecting to server...';

        const downloadBtn = document.getElementById('downloadPdfBtn');
        const recalculateBtn = document.getElementById('recalculateBtn');
        if (downloadBtn) {
            downloadBtn.disabled = true;
            downloadBtn.onclick = null;
        }

        if (recalculateBtn) {
            recalculateBtn.disabled = true;
            recalculateBtn.onclick = null;
        }

        const eventSource = new EventSource(`/api/progress/${jobID}`);

        eventSource.onopen = function() {
            console.log("SSE Connection established for Job:", jobID);
        };

        eventSource.onmessage = function(event) {
            statusText.innerHTML = event.data.replace(/\n/g, '<br>'); 
        };

        eventSource.addEventListener('ERROR', function(event) {
            console.error("Backend Error:", event.data);
            statusText.innerText = "Error: " + event.data;
            eventSource.close();
        });

        eventSource.addEventListener('COMPLETE', function(event) {
            console.log("Calculations finished!");
            eventSource.close();

            if (downloadBtn) {
                downloadBtn.disabled = false;
                downloadBtn.onclick = async function() {
                    //statusText.innerText = "Saving reports to local folder...";

                    try {
                        const response = await fetch(`/api/download_pdf/${jobID}`);
                        //const data = await response.json();
                    
                        if (response.ok) {
                            //statusText.innerText = data.message || "Reports saved and opened successfully!";
                        } else {
                            //statusText.innerText = "Error: " + (data.error || "Failed to save reports.");
                        }
                    } catch (error) {
                        console.error("Download endpoint error:", error);
                        //statusText.innerText = "Network error occurred while generating reports.";
                    }
                };
            }

            if (recalculateBtn) {
                recalculateBtn.disabled = false;
                const validationMsgDiv = document.getElementById('validationMessage');

                

                recalculateBtn.onclick = async function() {
                    const newDiscount = document.getElementById('job_discount').value || '0';
                    const discountValue = parseFloat(newDiscount);
                    validationMsgDiv.innerText = ""; // Clear previous validation messages
                    validationMsgDiv.style.display = 'none'; // Hide validation message initially
                    
                    
                    if (discountValue < 0 || discountValue > 100) {
                        validationMsgDiv.innerText = "Please enter a valid discount percentage between 0 and 100.";
                        validationMsgDiv.style.display = 'block';
                        return; // Stop execution and prevent the backend request
                    }
                    
                    const statusText = document.getElementById('uploadStatus');
                    statusText.innerText = 'Recalculating...';
                
                    
                    
                    const formData = new FormData();
                    formData.append('jobID', jobID);
                    formData.append('discountPercentage', newDiscount);
                
                    try {
                        const response = await fetch('/api/recalculate_profit', {
                            method: 'POST',
                            body: formData
                        });
                        if (response.ok) {
                            const data = await response.text();
                            statusText.innerHTML = data.replace(/\n/g, '<br>'); 
                        }else {
                            const errorMsg = await response.text();
                            statusText.innerText = `Error: ${errorMsg}`;
                        }
                    } catch (error) {
                        console.error('Error recalculating profit:', error);
                        statusText.innerText = 'Failed to recalculate.';
                        
                    }
                    
                };
            }

        });

        eventSource.onerror = function(err) {
            console.error("SSE Error:", err);
            // Only append connection error if stream wasn't closed intentionally
            if (eventSource.readyState === EventSource.CLOSED) {
                statusText.innerText += "\n[Connection closed]";
            } else {
                statusText.innerText += "\n[Connection closed or errored]";
            }
            eventSource.close();
            
        };

        const formData = new FormData();
        formData.append('pdfDocument', file);
        formData.append('jobID', jobID);
        formData.append('iccProfile', selectedIccProfile);

        formData.append('blackPrice', document.getElementById('monochrome_cost').value || '0');
        formData.append('blackYield', document.getElementById('monochrome_yield').value || '1');
        formData.append('isoCoverage', document.getElementById('covarage_percentage').value || '5');

        formData.append('targetPaperSize', target);
        formData.append('targetPaperArea', targetArea);
        formData.append('targetPaperCost', targetCost);

        //formData.append('printLayout', layout);
        formData.append('duplexMode', sides);
        formData.append('colorPageRange', color_page_range);
        formData.append('monochromePageRange', monochrome_page_range);
        
        formData.append('markupPercentage', document.getElementById('markup_percentage').value || '100');
        formData.append('minimumFee', document.getElementById('minimum_fee').value || '0');
        formData.append('finishingCost', document.getElementById('finishing_cost').value || '0');
        formData.append('discountPercentage', document.getElementById('job_discount').value || '0');

        if (colorMode !== 'Monochrome') {
            formData.append('cyanPrice', document.getElementById('cyanPrice').value || '0');
            formData.append('cyanYield', document.getElementById('cyanYield').value || '1');
            formData.append('magentaPrice', document.getElementById('magentaPrice').value || '0');
            formData.append('magentaYield', document.getElementById('magentaYield').value || '1');
            formData.append('yellowPrice', document.getElementById('yellowPrice').value || '0');
            formData.append('yellowYield', document.getElementById('yellowYield').value || '1');
        }

        // Append resolved values to formData:
        formData.append('printLayout', layout);
        formData.append('nUpFactor', nUpFactor);
        formData.append('shopName', document.getElementById('shop_name').value || '');
        formData.append('shopAddress', document.getElementById('shop_address').value || '');
        formData.append('shopContact', document.getElementById('shop_phone').value || '');





        try {
            statusText.innerText = 'Uploading...';
            const response = await fetch('/api/upload_pdf', {
                method: 'POST',
                body: formData
            });

            if (response.ok) {
                const result = await response.text();
                statusText.innerHTML = result.replace(/\n/g, '<br>');
            } else {
                statusText.innerText = 'Upload failed. Please try again.';
                eventSource.close();
            }
        } catch (error) {
            console.error('Error uploading PDF:', error);
            statusText.innerText = 'An error occurred during upload.';
            eventSource.close();
        }
    });

});


async function loadIccProfiles(preferred) {
    const select = document.getElementById('iccProfileSelect');
    if (!select) return;
    const previous = select.value;

    try {
        const response = await fetch('/api/icc_profiles');
        if (!response.ok) return;
        const profiles = await response.json();
        select.innerHTML = '';

        if (profiles.length === 0) {
            const opt = document.createElement('option');
            opt.value = 'USWebUncoated.icc';
            opt.textContent = 'USWebUncoated.icc (Default)';
            select.appendChild(opt);
            return;
        }

        profiles.forEach(profile => {
            const opt = document.createElement('option');
            opt.value = profile;
            opt.textContent = profile;
            select.appendChild(opt);
        });

        const wanted = [preferred, previous, 'USWebUncoated.icc'].find(n => n && profiles.includes(n));
        if (wanted) select.value = wanted;
    } catch (err) {
        console.error("Failed to load ICC profiles:", err);
    }
}

// Custom Paper Frontend Management
function updatePaperDropdown() {
    const select = document.getElementById('targetPaperSize');
    const currentValue = select.value; 
    select.innerHTML = '';
    
    // Add Standards
    standardPapers.forEach(p => {
        const opt = document.createElement('option');
        opt.value = p.name;
        opt.textContent = p.label;
        select.appendChild(opt);
    });

    // Add Customized
    customPapers.forEach(p => {
        const opt = document.createElement('option');
        opt.value = p.name;
        opt.textContent = `${p.name} (Custom Size)`;
        select.appendChild(opt);
    });

    // Retain previous selection if valid
    if (Array.from(select.options).some(opt => opt.value === currentValue)) {
        select.value = currentValue;
    }
}

// Load values into inputs for editing
window.editCustomPaper = function(index) {
    const p = customPapers[index];
    document.getElementById('customPaperName').value = p.name;
    // Convert points back to cm2 for user editing
    document.getElementById('customPaperArea').value = (p.areaInPoints / 803.521617).toFixed(2);
    document.getElementById('customPaperPrice').value = p.price;
    
    editingIndex = index;
    const addBtn = document.getElementById('addCustomPaperBtn');
    addBtn.textContent = 'Save Edit';
    addBtn.style.backgroundColor = '#10b981'; // Green color to indicate save state
};

// Ensure Delete can be reached from HTML buttons
window.removeCustomPaper = function(index) {
    customPapers.splice(index, 1);
    
    // If the user deletes the item they are currently editing, reset the inputs
    if (editingIndex === index) {
        editingIndex = -1;
        const addBtn = document.getElementById('addCustomPaperBtn');
        addBtn.textContent = 'Add';
        addBtn.style.backgroundColor = '';
        document.getElementById('customPaperName').value = '';
        document.getElementById('customPaperArea').value = '';
        document.getElementById('customPaperPrice').value = '';
    } else if (editingIndex > index) {
        // Adjust the editing index if a preceding item is deleted
        editingIndex--;
    }

    renderCustomPapers();
    updatePaperDropdown();
};

function renderCustomPapers() {
    const list = document.getElementById('customPaperList');
    list.innerHTML = '';
    customPapers.forEach((p, idx) => {
        const li = document.createElement('li');
        li.className = 'custom-paper-item';
        // Convert back to cm2 for rendering context
        const areaCm2 = (p.areaInPoints / 803.521617).toFixed(2);
        
        li.innerHTML = `
            <span>${p.name} - ${areaCm2} cm² - Rs ${p.price.toFixed(2)}</span>
            <div class="action-buttons">
                <button type="button" class="edit-btn" onclick="window.editCustomPaper(${idx})">Edit</button>
                <button type="button" class="delete-btn" onclick="window.removeCustomPaper(${idx})">Delete</button>
            </div>
        `;
        list.appendChild(li);
    });
}


// --- Helper: Gather Form Data into JSON Payload ---
function getSettingsFormData() {
    return {
        printerType: document.getElementById('printer_type').value,
        isoCoveragePercentage: parseFloat(document.getElementById('covarage_percentage').value) || 5.0,
        colorCapability: document.getElementById('color_capability').value,
        
        blackPrice: parseFloat(document.getElementById('monochrome_cost').value) || 0.0,
        blackYield: parseInt(document.getElementById('monochrome_yield').value, 10) || 0,
        
        cyanPrice: document.getElementById('cyanPrice').value ? parseFloat(document.getElementById('cyanPrice').value) : null,
        cyanYield: document.getElementById('cyanYield').value ? parseInt(document.getElementById('cyanYield').value, 10) : null,
        
        magentaPrice: document.getElementById('magentaPrice').value ? parseFloat(document.getElementById('magentaPrice').value) : null,
        magentaYield: document.getElementById('magentaYield').value ? parseInt(document.getElementById('magentaYield').value, 10) : null,
        
        yellowPrice: document.getElementById('yellowPrice').value ? parseFloat(document.getElementById('yellowPrice').value) : null,
        yellowYield: document.getElementById('yellowYield').value ? parseInt(document.getElementById('yellowYield').value, 10) : null,
        
        a4Cost: parseFloat(document.getElementById('A4').value) || 0.0,
        a3Cost: parseFloat(document.getElementById('A3').value) || 0.0,
        a5Cost: parseFloat(document.getElementById('A5').value) || 0.0,
        legalCost: parseFloat(document.getElementById('Legal').value) || 0.0,

        markupPercentage: parseFloat(document.getElementById('markup_percentage').value) || 0.0,
        minimumFee: parseFloat(document.getElementById('minimum_fee').value) || 0.0,
        finishingCost: parseFloat(document.getElementById('finishing_cost').value) || 0.0,

        shopName: document.getElementById('shop_name').value || '',
        shopAddress: document.getElementById('shop_address').value || '',
        shopContact: document.getElementById('shop_phone').value || '',

        customPapers: customPapers 

        
    };
}


async function sendSettingsRequest(url, method, successMessage) {
    const settingsData = getSettingsFormData();
    const setting_massage = document.getElementById('settingsStatus');

    const colorCapability = document.getElementById('color_capability').value;

    if (colorCapability !== 'Monochrome') {
        const cyanPrice = document.getElementById('cyanPrice').value.trim();
        const cyanYield = document.getElementById('cyanYield').value.trim();
        const magentaPrice = document.getElementById('magentaPrice').value.trim();
        const magentaYield = document.getElementById('magentaYield').value.trim();
        const yellowPrice = document.getElementById('yellowPrice').value.trim();
        const yellowYield = document.getElementById('yellowYield').value.trim();

        if (!cyanPrice || !cyanYield || !magentaPrice || !magentaYield || !yellowPrice || !yellowYield) {
            setting_massage.innerText = 'Error: All Cyan, Magenta, and Yellow price and yield fields must be filled when Full Color is selected.';
            setting_massage.className = 'status-error';
            return false;
        }
    }

    try {
        const response = await fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(settingsData)
        });

        const message = await response.text();
        setting_massage.innerText = message;
        setting_massage.className = response.ok ? 'status-ok' : 'status-error';
        return response.ok;
    } catch (error) {
        console.error('Error processing printer settings:', error);
        setting_massage.innerText = 'A network error occurred.';
        setting_massage.className = 'status-error';
        return false;
    }
}

// --- Helper: Load Saved Settings from Backend ---
async function loadSavedSettings() {
    try {
        const response = await fetch('/api/settings/get',{
            method:'GET',
            credentials: 'include' 
        });
        if (response.ok) {
            const data = await response.json();
            settingsSaved = true;
            
            document.getElementById('printer_type').value = data.printerType || 'inkjet';
            document.getElementById('covarage_percentage').value = data.isoCoveragePercentage ?? 5;
            document.getElementById('color_capability').value = data.colorCapability || 'color';
            
            document.getElementById('monochrome_cost').value = data.blackPrice ?? '';
            document.getElementById('monochrome_yield').value = data.blackYield ?? '';
            
            document.getElementById('cyanPrice').value = data.cyanPrice ?? '';
            document.getElementById('cyanYield').value = data.cyanYield ?? '';
            document.getElementById('magentaPrice').value = data.magentaPrice ?? '';
            document.getElementById('magentaYield').value = data.magentaYield ?? '';
            document.getElementById('yellowPrice').value = data.yellowPrice ?? '';
            document.getElementById('yellowYield').value = data.yellowYield ?? '';
            
            document.getElementById('A4').value = data.a4Cost ?? '';
            document.getElementById('A3').value = data.a3Cost ?? '';
            document.getElementById('A5').value = data.a5Cost ?? '';
            document.getElementById('Legal').value = data.legalCost ?? '';

            document.getElementById('markup_percentage').value = data.markupPercentage ?? 100;
            document.getElementById('minimum_fee').value = data.minimumFee ?? 0;
            document.getElementById('finishing_cost').value = data.finishingCost ?? 0;

            document.getElementById('shop_name').value = data.shopName || '';
            document.getElementById('shop_address').value = data.shopAddress || '';
            document.getElementById('shop_phone').value = data.shopContact || '';

            customPapers = data.customPapers || [];

            document.getElementById('color_capability').dispatchEvent(new Event('change'));
            
            renderCustomPapers();
            updatePaperDropdown();
        } else {
            // Load defaults if none
            updatePaperDropdown();
        }
    } catch (error) {
        console.log('No saved settings found for current session.');
        updatePaperDropdown();
    }
}

function switchTab(event, tabId) {
    const contents = document.querySelectorAll('.tab-content');
    contents.forEach(tab => tab.style.display = 'none');

    const buttons = document.querySelectorAll('.tab-btn');
    buttons.forEach(btn => btn.classList.remove('active'));

    document.getElementById(tabId).style.display = 'block';
    event.currentTarget.classList.add('active');
}

async function uploadLogo() {
    const fileInput = document.getElementById('logoInput');
    const statusText = document.getElementById('logoStatus');
    
    if (fileInput.files.length === 0) {
        statusText.innerText = "Please select a file first.";
        statusText.style.color = "red";
        return;
    }

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);

    try {
        statusText.innerText = "Uploading...";
        statusText.style.color = "blue";
        
        const response = await fetch('/api/logo/upload', {
            method: 'POST',
            body: formData
        });

        if (response.ok) {
            statusText.innerText = "Logo updated successfully!";
            statusText.style.color = "green";
            
            const logoImg = document.getElementById('shopLogo');
            logoImg.src = `/api/logo?t=${new Date().getTime()}`;
            
            fileInput.value = "";
        } else {
            const errorMsg = await response.text();
            statusText.innerText = `Failed: ${errorMsg}`;
            statusText.style.color = "red";
        }
    } catch (error) {
        statusText.innerText = "Network error occurred.";
        statusText.style.color = "red";
    }
}

function setIccStatus(message, color) {
    const el = document.getElementById('iccStatus');
    el.innerText = message;
    el.style.color = color;
}

async function uploadIccProfile() {
    const fileInput = document.getElementById('iccInput');
    if (fileInput.files.length === 0) {
        setIccStatus('Please choose a profile file first.', '#f87171');
        return;
    }
    const formData = new FormData();
    formData.append('file', fileInput.files[0]);

    try {
        setIccStatus('Adding...', '#93c5fd');
        const response = await fetch('/api/icc_profiles/upload', { method: 'POST', body: formData });
        const data = await response.json().catch(() => ({}));

        if (response.ok) {
            setIccStatus('Added: ' + data.name, '#4ade80');
            fileInput.value = '';
            await loadIccProfiles();
            await loadCustomIccProfiles();
        } else {
            setIccStatus(data.error || 'Could not add the profile (is the file too large?).', '#f87171');
        }
    } catch (e) {
        setIccStatus('Network error occurred.', '#f87171');
    }
}

async function loadCustomIccProfiles() {
    const select = document.getElementById('customIccSelect');
    const btn = document.getElementById('removeIccBtn');
    if (!select || !btn) return;
    try {
        const response = await fetch('/api/icc_profiles/custom');
        const names = response.ok ? await response.json() : [];
        select.innerHTML = '';
        if (names.length === 0) {
            const opt = document.createElement('option');
            opt.value = '';
            opt.textContent = 'No added profiles yet';
            select.appendChild(opt);
        } else {
            names.forEach(n => {
                const opt = document.createElement('option');
                opt.value = n;
                opt.textContent = n;
                select.appendChild(opt);
            });
        }
        select.disabled = btn.disabled = names.length === 0;
    } catch (e) {
        console.error('Failed to load added profiles:', e);
    }
}

async function removeIccProfile() {
    const name = document.getElementById('customIccSelect').value;
    if (!name) return;
    try {
        const response = await fetch('/api/icc_profiles?name=' + encodeURIComponent(name), { method: 'DELETE' });
        const data = await response.json().catch(() => ({}));
        if (response.ok) {
            setIccStatus('Removed: ' + name, '#4ade80');
            await loadIccProfiles();
            await loadCustomIccProfiles();
        } else {
            setIccStatus(data.error || 'Could not remove the profile.', '#f87171');
        }
    } catch (e) {
        setIccStatus('Network error occurred.', '#f87171');
    }
}