document.getElementById('color_capability').addEventListener('change', function() {
    const full_color = document.getElementById('full_color');
    const printering_color_type = document.getElementById('colorTypeSelect');
    
    if (this.value === 'Monochrome') {
        // Hide the color inputs
        full_color.style.display = 'none';
        printering_color_type.value = 'Monochrome';
        
        printering_color_type.disabled = true; // Disable the color type select when monochrome is selected
        printering_color_type.dispatchEvent(new Event('change'));
        
        // Optional: Clear out any data they might have typed before switching to monochrome
        document.getElementById('cyanPrice').value = '';
        document.getElementById('cyanYield').value = '';
        document.getElementById('magentaPrice').value = '';
        document.getElementById('magentaYield').value = '';
        document.getElementById('yellowPrice').value = '';
        document.getElementById('yellowYield').value = '';
    } else {
        // Show the color inputs
        full_color.style.display = 'block';
        printering_color_type.value = 'fullColor';
        printering_color_type.disabled = false; // Enable the color type select when full color is selected
        printering_color_type.dispatchEvent(new Event('change'));
    }
});



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
        statusText.innerText = event.data;

        if(event.data === 'complete') {
            eventSource.close();
        }
    }

    const formData = new FormData();
    formData.append('pdfDocument', file);
    formData.append('colorCapability', colorMode);
    formData.append('jobID', jobID);

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

    try{
        document.getElementById('uploadStatus').innerText = 'Uploading...';
        const response = await fetch('api/upload_pdf', {
            method: 'POST',
            body: formData
        });
        if(response.ok){
            const result = await response.text();
            document.getElementById('uploadStatus').innerText =  result;
        }else{
            document.getElementById('uploadStatus').innerText = 'Upload failed. Please try again.';
        }
    }catch(error){
        console.error('Error uploading PDF:', error);
        document.getElementById('uploadStatus').innerText = 'An error occurred during upload.';
    }
});