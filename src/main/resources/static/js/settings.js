// Settings page script: handles password update and level upgrade

(function () {
    const passwordForm = document.getElementById('passwordForm');
    const currentPassword = document.getElementById('currentPassword');
    const newPassword = document.getElementById('newPassword');
    const confirmPassword = document.getElementById('confirmPassword');
    const savePasswordBtn = document.getElementById('savePasswordBtn');
    const passwordFeedback = document.getElementById('passwordFeedback');

    const upgradeLevelBtn = document.getElementById('upgradeLevelBtn');
    const levelFeedback = document.getElementById('levelFeedback');

    function setFeedback(el, message, isError = false) {
        if (!el) return;
        el.textContent = message || '';
        el.classList.remove('text-red-600', 'text-green-600');
        el.classList.add(isError ? 'text-red-600' : 'text-green-600');
    }

    function validatePasswords() {
        const curr = currentPassword.value.trim();
        const np = newPassword.value.trim();
        const cp = confirmPassword.value.trim();
        if (!curr || !np || !cp) {
            setFeedback(passwordFeedback, 'Please fill out all fields', true);
            return false;
        }
        if (np.length < 8) {
            setFeedback(passwordFeedback, 'New password must be at least 8 characters', true);
            return false;
        }
        if (np !== cp) {
            setFeedback(passwordFeedback, 'Passwords do not match', true);
            return false;
        }
        setFeedback(passwordFeedback, '');
        return true;
    }

    async function saveLevel() {
        const selected = document.querySelector('input.level-radio:checked');
        if (!selected) {
            setFeedback(levelFeedback, 'Select a level first', true);
            return;
        }
        // Highlight chosen tier
        document.querySelectorAll('.tier-card').forEach(c => c.classList.remove('active'));
        const chosen = selected.closest('.tier-card');
        if (chosen) chosen.classList.add('active');
        const level = Number(selected.value);
        setFeedback(levelFeedback, 'Saving level...');
        upgradeLevelBtn.disabled = true;
//        try {
//            const res = await fetch('/api-v/settings/level', {
//                method: 'POST',
//                headers: { 'Content-Type': 'application/json' },
//                body: JSON.stringify({ level })
//            });
//            if (!res.ok) throw new Error('Failed to save level');
//            setFeedback(levelFeedback, `Level updated to ${level}`);
//        } catch (e) {
//            setFeedback(levelFeedback, e.message || 'Error updating level', true);
//        } finally {
//            upgradeLevelBtn.disabled = false;
//        }
    }

//    if (savePasswordBtn) savePasswordBtn.addEventListener('click', updatePassword);
    if (upgradeLevelBtn) upgradeLevelBtn.addEventListener('click', saveLevel);
})();


