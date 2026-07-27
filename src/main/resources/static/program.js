document.addEventListener('DOMContentLoaded', () => {
    // 1. Auto-load existing user settings on page load
    loadSavedSettings();
    

    // 2. Event Listener: Color Capability Dropdown Toggle
    document.getElementById('color_capability').addEventListener('change', function() {
        const full_color = document.getElementById('full_color');
        const colorTypeSelect = document.getElementById('colorTypeSelect');
        
        if (this.value === 'Monochrome') {
            full_color.style.display = 'none';
            colorTypeSelect.value = 'Monochrome';
            colorTypeSelect.disabled = true; 
            
            // Clear out color inputs when switching to monochrome
            document.getElementById('cyanPrice').value = '';
            document.getElementById('cyanYield').value = '';
            document.getElementById('magentaPrice').value = '';
            document.getElementById('magentaYield').value = '';
            document.getElementById('yellowPrice').value = '';
            document.getElementById('yellowYield').value = '';
        } else {
            full_color.style.display = 'block';
            colorTypeSelect.disabled = false;
        }
        colorTypeSelect.dispatchEvent(new Event('change'));
    });

    // 3. Event Listener: Save Button (Form Submission)
    document.getElementById('inkCostForm').addEventListener('submit', function(event) {
        event.preventDefault();
        sendSettingsRequest('/api/settings/create', 'POST', 'Settings created successfully!')
    });

    // 4. Event Listener: Update Button
    document.getElementById('updateButton').addEventListener('click', function() {
       sendSettingsRequest('/api/settings/update', 'PUT', 'Settings updated successfully!');
    });

    // 5. Event Listener: Clear Settings Button
    // 5. Event Listener: Clear Settings Button
    document.getElementById('clear_settings').addEventListener('click', async function() {
        // Step A: Clear the UI Form
        document.getElementById('inkCostForm').reset();
        document.getElementById('covarage_percentage').value = 5; // Reset to default ISO percentage
        document.getElementById('color_capability').dispatchEvent(new Event('change')); // Reset UI state

        // Step B: Erase from the Database
        try {
            const response = await fetch('/api/settings/clear', {
                method: 'DELETE'
            });

            const message = await response.text();
            if (response.ok) {
                console.log(message);
                document.getElementById('settingsStatus').innerText = message;
                //alert("Settings cleared from form and database!");
            } else {
                document.getElementById('settingsStatus').innerText = message;
                //alert('Error: ' + message);
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
        const colorMode = document.getElementById('colorTypeSelect').value;

        if (!file) {
            document.getElementById('uploadStatus').innerText = 'Please select a PDF file.';
            return;
        }

        const jobID = crypto.randomUUID();
        const statusText = document.getElementById('uploadStatus');
        statusText.innerText = 'Connecting to server...';

        const eventSource = new EventSource(`/api/progress/${jobID}`);

        eventSource.onmessage = function(event) {
            statusText.innerHTML = event.data.replace(/\n/g, '<br>'); // Replace newlines with <br> for HTML display

            if (event.data.includes('Analysis Complete!') || event.data.includes('Error')) {
                eventSource.close();
            }
        };

        const formData = new FormData();
        formData.append('pdfDocument', file);
        formData.append('colorCapability', colorMode);
        formData.append('jobID', jobID);
        formData.append('paperCost', document.getElementById('paperCost').value || '0');

        formData.append('blackPrice', document.getElementById('monochrome_cost').value || '0');
        formData.append('blackYield', document.getElementById('monochrome_yield').value || '1');
        formData.append('isoCoverage', document.getElementById('covarage_percentage').value || '5');

        if (colorMode !== 'Monochrome') {
            formData.append('cyanPrice', document.getElementById('cyanPrice').value || '0');
            formData.append('cyanYield', document.getElementById('cyanYield').value || '1');
            formData.append('magentaPrice', document.getElementById('magentaPrice').value || '0');
            formData.append('magentaYield', document.getElementById('magentaYield').value || '1');
            formData.append('yellowPrice', document.getElementById('yellowPrice').value || '0');
            formData.append('yellowYield', document.getElementById('yellowYield').value || '1');
        }

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
        
        printJobColorType: document.getElementById('colorTypeSelect').value,
        paperCost: parseFloat(document.getElementById('paperCost').value) || 0.0
    };
}


async function sendSettingsRequest(url, method, successMessage) {
    const settingsData = getSettingsFormData();
    const setting_massage = document.getElementById('settingsStatus');

    const colorCapability = document.getElementById('color_capability').value;

    // --- SECURITY CHECK: Validate Full Color Fields if Full Color is selected ---
    if (colorCapability !== 'Monochrome') {
        const cyanPrice = document.getElementById('cyanPrice').value.trim();
        const cyanYield = document.getElementById('cyanYield').value.trim();
        const magentaPrice = document.getElementById('magentaPrice').value.trim();
        const magentaYield = document.getElementById('magentaYield').value.trim();
        const yellowPrice = document.getElementById('yellowPrice').value.trim();
        const yellowYield = document.getElementById('yellowYield').value.trim();

        if (!cyanPrice || !cyanYield || !magentaPrice || !magentaYield || !yellowPrice || !yellowYield) {
            setting_massage.innerText = 'Error: All Cyan, Magenta, and Yellow price and yield fields must be filled when Full Color is selected.';
            return; // Stops the function from executing the fetch request
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
        if (response.ok) {
            setting_massage.innerText = message;
            //alert(successMessage);
        } else {
            setting_massage.innerText = message;
            //alert('Error: ' + message);
        }
    } catch (error) {
        console.error('Error processing printer settings:', error);
        alert('A network error occurred.');
    }
}

// --- Helper: Load Saved Settings from Backend ---
async function loadSavedSettings() {
    try {
        const response = await fetch('/api/settings/get',{
            method:'GET',
            credentials: 'include' // Ensure cookies/session are sent with the request
        });
        if (response.ok) {
            const data = await response.json();
            
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
            
            document.getElementById('colorTypeSelect').value = data.printJobColorType || 'fullColor';
            document.getElementById('paperCost').value = data.paperCost ?? '';
            
            // Sync visible sections according to restored color capability
            document.getElementById('color_capability').dispatchEvent(new Event('change'));
        }
    } catch (error) {
        console.log('No saved settings found for current session.');
    }
}

// Event Listener: Logout Button
    document.getElementById('logoutButton').addEventListener('click', async function() {
        try {
            await fetch('/api/logout', {
                method: 'POST'
            });
        } catch (error) {
            console.error('Error during logout request:', error);
        } finally {
            // Always redirect to the login page, even if network fails
            window.location.href = 'index.html';
        }
    });