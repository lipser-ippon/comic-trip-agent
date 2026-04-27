async function confirmAndDeleteTrip(tripId) {
    const confirmation = window.prompt("Pour confirmer la suppression, tapez 'SUPPRIMER' dans la boîte ci-dessous :");

    if (confirmation === 'SUPPRIMER') {
        try {
            const response = await fetch(`/api/mission-control/trips/${tripId}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                alert("Voyage supprimé avec succès !");
                window.location.href = '/'; // Redirect to home page
            } else {
                const errorText = await response.text();
                alert(`Échec de la suppression du voyage: ${errorText}`);
                console.error('Delete error:', errorText);
            }
        } catch (error) {
            alert("Une erreur est survenue lors de la suppression du voyage.");
            console.error('Network or unexpected error during deletion:', error);
        }
    } else if (confirmation !== null) { // User typed something but not "SUPPRIMER"
        alert("Suppression annulée. Confirmation incorrecte.");
    } else { // User clicked cancel on the prompt
        alert("Suppression annulée.");
    }
}
