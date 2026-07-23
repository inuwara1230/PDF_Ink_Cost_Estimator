document.getElementById('loginForm').addEventListener('submit', async function(e) {
    e.preventDefault(); // Stop page from refreshing

    const usernameInput = document.getElementById('username').value;
    const passwordInput = document.getElementById('password').value;

    const credentials = {
        username: usernameInput,
        password: passwordInput
    };

    try {
        const response = await fetch('/api/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(credentials)
        });

        if (response.ok) {
            
            window.location.href = 'program.html'; 
        } else {
            const errorText = await response.text();
            document.getElementById('errorMsg').innerText = errorText;
        }
    } catch (error) {
        console.error('Error during login:', error);
        document.getElementById('errorMsg').innerText = 'An error occurred. Try again.';
    }
});