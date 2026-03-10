const uploadBtn = document.getElementById('upload-btn');
const fileInput = document.getElementById('file-input');
const statusIcon = document.getElementById('upload-status-icon');
const statusTitle = document.getElementById('upload-status-title');
const statusMsg = document.getElementById('upload-status-msg');
const dropZone = uploadBtn.closest('.halftone');

uploadBtn.addEventListener('click', () => fileInput.click());

// Drag and Drop listeners
['dragenter', 'dragover'].forEach(eventName => {
    dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.add('bg-accent-yellow', 'bg-opacity-10');
    }, false);
});

['dragleave', 'drop'].forEach(eventName => {
    dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.remove('bg-accent-yellow', 'bg-opacity-10');
    }, false);
});

dropZone.addEventListener('drop', (e) => {
    const dt = e.dataTransfer;
    const files = dt.files;
    handleFiles(files);
}, false);

fileInput.addEventListener('change', (e) => {
    handleFiles(e.target.files);
});

async function handleFiles(files) {
    if (!files || files.length === 0) return;

    // UI Feedback: Loading
    uploadBtn.disabled = true;
    uploadBtn.classList.add('opacity-50', 'cursor-not-allowed');
    statusTitle.innerText = files.length > 1 ? "Processing Squad..." : "Processing...";
    statusMsg.innerText = "Our AI illustrators are at work!";
    statusIcon.classList.add('animate-bounce');

    const formData = new FormData();
    for (let i = 0; i < files.length; i++) {
        formData.append('file', files[i]);
    }

    try {
        const response = await fetch('/api/mission-control/upload', {
            method: 'POST',
            body: formData
        });

        if (response.ok) {
            const data = await response.json();
            console.log('Upload success:', data);

            const tripId = data.tripId;
            const tripTitle = data.title || ('Trigp ' + tripId);
            const pictures = data.pictures || [];

            statusTitle.innerText = "Success!";
            statusMsg.innerText = `${pictures.length} comic panels generated! Redirecting...`;
            statusIcon.classList.remove('animate-bounce');
            statusIcon.innerHTML = `<span class="material-symbols-outlined text-5xl">check_circle</span>`;
            statusIcon.classList.replace('bg-primary', 'bg-green-500');

            setTimeout(() => {
                window.location.href = '/trips/' + tripId;
            }, 2000);
        } else {
            const errorText = await response.text();
            throw new Error(`Upload failed with status ${response.status}: ${errorText}`);
        }
    } catch (error) {
        console.error('Upload error:', error);
        statusTitle.innerText = "Abort Mission!";
        statusMsg.innerText = `Error: ${error.message}. Try again, hero.`;
        statusIcon.classList.remove('animate-bounce');
        statusIcon.innerHTML = `<span class="material-symbols-outlined text-5xl">error</span>`;
        statusIcon.classList.replace('bg-primary', 'bg-red-500');

        uploadBtn.disabled = false;
        uploadBtn.classList.remove('opacity-50', 'cursor-not-allowed');
    }
}
