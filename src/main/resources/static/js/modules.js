(function(){
    const search = document.getElementById('moduleSearch');
    const bookingModal = document.getElementById('bookingModal');
    const closeBooking = document.getElementById('closeBooking');
    const bookingModuleName = document.getElementById('bookingModuleName');
    const bookingPrice = document.getElementById('bookingPrice');
    const bookingDate = document.getElementById('bookingDate');
    const confirmBooking = document.getElementById('confirmBooking');
    const bookingFeedback = document.getElementById('bookingFeedback');

    let selectedModuleId = null;
    let selectedModuleName = '';
    let selectedModulePrice = 0;

    function money(n){
        try { return new Intl.NumberFormat(undefined, {style:'currency', currency:'USD'}).format(n || 0); } catch(e){ return `$${Number(n||0).toFixed(2)}` }
    }

    function openBooking(moduleId, moduleName, modulePrice){
        selectedModuleId = moduleId;
        selectedModuleName = moduleName;
        selectedModulePrice = modulePrice;
        bookingModuleName.textContent = moduleName || '';
        bookingPrice.textContent = money(modulePrice);
        bookingDate.value = '';
        bookingFeedback.textContent = '';
        bookingModal.classList.remove('hidden');
        bookingModal.classList.add('flex');
    }

    function closeBookingModal(){
        bookingModal.classList.add('hidden');
        bookingModal.classList.remove('flex');
    }

    async function confirm(){
        if (!selectedModuleId){ return; }
        const date = bookingDate.value;
        if (!date){ bookingFeedback.textContent = 'Pick a date'; bookingFeedback.classList.add('text-red-600'); return; }
        bookingFeedback.textContent = 'Processing...'; bookingFeedback.classList.remove('text-red-600'); bookingFeedback.classList.add('text-gray-600');
        try{
            const payload = {
                moduleId: selectedModuleId,
                description: `Booking for ${selectedModuleName || 'module'}`,
                startDate: date,
                startTime: '09:00',
                endDate: date,
                endTime: '10:00',
                duration: 60,
                users: []
            };
            const res = await fetch('/api-v/session/create', {
                method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify(payload)
            });
            if (!res.ok) throw new Error('Failed to book module');
            bookingFeedback.textContent = 'Booked successfully!'; bookingFeedback.classList.remove('text-gray-600'); bookingFeedback.classList.add('text-green-600');
            setTimeout(closeBookingModal, 1200);
        }catch(e){
            bookingFeedback.textContent = e.message || 'Error booking'; bookingFeedback.classList.remove('text-gray-600'); bookingFeedback.classList.add('text-red-600');
        }
    }

    // Search functionality
    if (search){
        search.addEventListener('input', () => {
            const q = search.value.toLowerCase();
            const cards = document.querySelectorAll('.module-card');
            cards.forEach(card => {
                const name = card.querySelector('h3').textContent.toLowerCase();
                const description = card.querySelector('p').textContent.toLowerCase();
                if (name.includes(q) || description.includes(q)) {
                    card.style.display = 'block';
                } else {
                    card.style.display = 'none';
                }
            });
        });
    }

    if (closeBooking) closeBooking.addEventListener('click', closeBookingModal);
    if (confirmBooking) confirmBooking.addEventListener('click', confirm);

    // Make openBooking function globally available for Thymeleaf onclick
    window.openBooking = openBooking;
})();


