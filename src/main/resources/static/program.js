document.getElementById('color_capability').addEventListener('change', function() {
    const full_color = document.getElementById('full_color');
    
    if (this.value === 'Monochrome') {
        // Hide the color inputs
        full_color.style.display = 'none';
        
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