async function handleCredentialResponse(response) {
    try {
        const res = await fetch('/api/google-login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ token: response.credential })
        });

        if (res.ok) {
            window.location.href = 'program.html'; 
        } else {
            const errorText = await res.text();
            document.getElementById('errorMsg').innerText = errorText;
        }
    } catch (error) {
        console.error('Error during Google login:', error);
        document.getElementById('errorMsg').innerText = 'Google Login failed. Try again.';
    }
}