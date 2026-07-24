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