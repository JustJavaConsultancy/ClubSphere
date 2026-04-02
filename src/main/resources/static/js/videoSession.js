// Enhanced Video Call Module using ZegoCloud with Retry Logic and Loading States
class VideoCallManager {
    constructor() {
        this.appID = 382757591;
        this.serverSecret = "a3a34788c4745c28fa325ebae4e8b0f5";
        this.userInfo = null;
        this.isReady = false;
        this.retryAttempts = 0;
        this.maxRetries = 3;
        this.retryDelay = 1000; // Start with 1 second
        this.loadingButtons = new Set();

        this.init();
    }

    async init() {
        console.log('Initializing Enhanced Video Call Manager...');
        await this.loadUserInfo();
        await this.waitForDependencies();
        this.setupEventListeners();
        this.enableVideoButtons();
        this.isReady = true;
        console.log('Video Call Manager ready');

        // Dispatch ready event
        document.dispatchEvent(new CustomEvent('videoManagerReady'));
    }

    async waitForDependencies() {
        const dependencies = [
            () => window.ZegoUIKitPrebuilt,
            () => document.readyState === 'complete' || document.readyState === 'interactive'
        ];

        for (const checkDependency of dependencies) {
            await this.waitForCondition(checkDependency, 'dependency');
        }

        console.log('All video call dependencies loaded successfully');
    }

    waitForCondition(condition, name, timeout = 30000) {
        return new Promise((resolve, reject) => {
            const startTime = Date.now();

            const check = () => {
                if (condition()) {
                    console.log(`${name} is ready`);
                    resolve();
                } else if (Date.now() - startTime > timeout) {
                    console.error(`Timeout waiting for ${name}`);
                    reject(new Error(`Timeout waiting for ${name}`));
                } else {
                    setTimeout(check, 100);
                }
            };

            check();
        });
    }

    setupEventListeners() {
        // Use event delegation for dynamically added buttons
        document.addEventListener('click', (e) => {
            const videoButton = e.target.closest('.start-video-call-user, .start-video-call-meeting');
            if (videoButton) {
                // Multiple checks to ensure button is truly enabled
                const isDisabled = videoButton.disabled ||
                                 videoButton.hasAttribute('disabled') ||
                                 videoButton.getAttribute('disabled') === 'true' ||
                                 videoButton.getAttribute('disabled') === 'disabled' ||
                                 this.loadingButtons.has(videoButton) ||
                                 videoButton.style.backgroundColor === 'gray' ||
                                 videoButton.style.backgroundColor === 'rgb(156, 163, 175)';

                if (isDisabled) {
                    e.preventDefault();
                    e.stopPropagation();
                    console.log('Video call button is disabled, ignoring click');
                    return false;
                }

                e.preventDefault();
                this.handleVideoCall(videoButton);
            }
        });
    }

    async loadUserInfo() {
        try {
            const response = await fetch('/api/chat/video-call/user-info');
            if (response.ok) {
                this.userInfo = await response.json();
                console.log('Video call user info loaded:', this.userInfo);
            } else {
                console.warn('Failed to load user info, using fallback');
                this.userInfo = {
                    userId: 'user_' + Math.floor(Math.random() * 10000),
                    userName: 'User'
                };
            }
        } catch (error) {
            console.error('Error loading user info:', error);
            this.userInfo = {
                userId: 'user_' + Math.floor(Math.random() * 10000),
                userName: 'User'
            };
        }
    }

    async handleVideoCall(button, retryCount = 0) {
        try {
            // Double-check if button is disabled before proceeding
            const isDisabled = button.disabled ||
                             button.hasAttribute('disabled') ||
                             button.getAttribute('disabled') === 'true' ||
                             button.getAttribute('disabled') === 'disabled' ||
                             this.loadingButtons.has(button) ||
                             button.style.backgroundColor === 'gray' ||
                             button.style.backgroundColor === 'rgb(156, 163, 175)';

            if (isDisabled) {
                console.log('Button is disabled, aborting video call');
                return;
            }

            // Set loading state
            this.setButtonLoading(button, true);

            // Check if manager is ready
            if (!this.isReady || !this.checkConnectivity()) {
                if (retryCount < this.maxRetries) {
                    console.log(`Retry attempt ${retryCount + 1}/${this.maxRetries}`);
                    await this.delay(this.retryDelay * Math.pow(2, retryCount)); // Exponential backoff
                    return this.handleVideoCall(button, retryCount + 1);
                } else {
                    throw new Error('Video call service is not available');
                }
            }

            // Wait for user info to be loaded if not already
            if (!this.userInfo) {
                await this.loadUserInfo();
            }

            // Attempt to start video call
            await this.startVideoCall(button);

            // Success - reset retry count
            this.retryAttempts = 0;

        } catch (error) {
            console.error('Video call failed:', error);

            if (retryCount < this.maxRetries) {
                console.log(`Retrying video call... Attempt ${retryCount + 1}/${this.maxRetries}`);
                await this.delay(this.retryDelay * Math.pow(2, retryCount));
                return this.handleVideoCall(button, retryCount + 1);
            } else {
                this.showError(button, 'Unable to start video call. Please check your connection and try again.');
            }
        } finally {
            // Always remove loading state
            this.setButtonLoading(button, false);
        }
    }

    async startVideoCall(button = null) {
        return new Promise((resolve, reject) => {
            try {
                // Get session ID from the button's context or fallback to existing logic
                let sessionID;

                if (button) {
                    // Try to get session ID from button data attributes
                    sessionID = button.dataset.sessionId || button.dataset.meetingId;

                    // If not found, look for sessionID input in the same container
                    if (!sessionID) {
                        const container = button.closest('.coaching-card, .meeting-card');
                        const sessionInput = container?.querySelector('#sessionID, .meetingID');
                        sessionID = sessionInput?.value;
                    }
                }

                // Fallback to original logic
                if (!sessionID) {
                    const sessionElement = document.getElementById('sessionID');
                    sessionID = sessionElement?.value;
                }

                if (!sessionID) {
                    throw new Error('Session ID not found');
                }

                // Generate room ID and meeting URL
                const roomId = this.generateRoomId(sessionID);
                const baseUrl = window.location.protocol + '//' + window.location.host;
                const meetingUrl = `${baseUrl}/videocall?roomID=${roomId}&userName=${encodeURIComponent(this.userInfo.userName)}&userID=${encodeURIComponent(this.userInfo.userId)}`;

                // Open in new tab
                const newWindow = window.open(meetingUrl, '_blank');

                if (newWindow) {
                    console.log('Video call opened successfully');
                    resolve();
                } else {
                    throw new Error('Failed to open video call window. Please check popup blocker settings.');
                }
            } catch (error) {
                reject(error);
            }
        });
    }

    setButtonLoading(button, isLoading) {
        if (isLoading) {
            this.loadingButtons.add(button);
            button.disabled = true;

            // Store original content
            if (!button.dataset.originalContent) {
                button.dataset.originalContent = button.innerHTML;
            }

            // Set loading content based on button type
            const isMobile = button.closest('[layout\\:fragment="content"]')?.querySelector('.mobile-card');
            const buttonContent = button.querySelector('.button-content');

            if (isMobile) {
                if (buttonContent) {
                    buttonContent.innerHTML = `
                        <span class="material-icons animate-spin">refresh</span>
                        Connecting...
                    `;
                } else {
                    button.innerHTML = `
                        <span class="material-icons animate-spin">refresh</span>
                        Connecting...
                    `;
                }
            } else {
                if (buttonContent) {
                    buttonContent.innerHTML = `
                        <svg class="animate-spin h-4 w-4 mr-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                        Connecting...
                    `;
                } else {
                    button.innerHTML = `
                        <svg class="animate-spin h-4 w-4 mr-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                        Connecting...
                    `;
                }
            }
        } else {
            this.loadingButtons.delete(button);
            button.disabled = false;

            // Restore original content
            if (button.dataset.originalContent) {
                button.innerHTML = button.dataset.originalContent;
            }
        }
    }

    showError(button, message) {
        // Show error state briefly
        const originalContent = button.innerHTML;
        const isMobile = button.closest('[layout\\:fragment="content"]')?.querySelector('.mobile-card');

        if (isMobile) {
            button.innerHTML = `
                <span class="material-icons" style="color: #ef4444;">error</span>
                Failed
            `;
        } else {
            button.innerHTML = `
                <span style="color: #ef4444;">⚠️ Failed</span>
            `;
        }

        // Show user-friendly error message
        if (typeof alert !== 'undefined') {
            alert(message);
        } else {
            console.error(message);
        }

        // Restore button after 2 seconds
        setTimeout(() => {
            if (button.dataset.originalContent) {
                button.innerHTML = button.dataset.originalContent;
            }
        }, 2000);
    }

    checkConnectivity() {
        return navigator.onLine &&
               window.ZegoUIKitPrebuilt &&
               this.userInfo;
    }

    enableVideoButtons() {
        const buttons = document.querySelectorAll('.start-video-call-user, .start-video-call-meeting');
        buttons.forEach(button => {
            // Only enable buttons that are not explicitly disabled by the application logic
            // Check if the button should be disabled based on the isReady state
            const shouldBeDisabled = button.hasAttribute('data-originally-disabled') ||
                                   button.style.backgroundColor === 'gray' ||
                                   button.style.backgroundColor === 'rgb(156, 163, 175)' ||
                                   button.getAttribute('th:disabled') === 'true';

            if (!shouldBeDisabled && button.disabled && !this.loadingButtons.has(button)) {
                button.disabled = false;
                // Remove any loading text that might be there
                if (button.textContent.includes('Loading') || button.textContent.includes('Connecting')) {
                    if (button.dataset.originalContent) {
                        button.innerHTML = button.dataset.originalContent;
                    }
                }
            }
        });
        console.log(`Processed ${buttons.length} video call buttons`);
    }

    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    generateRoomId(chatId) {
        // Generate a clean room ID based on chat ID
        return `room_${chatId}`.replace(/[^a-zA-Z0-9_]/g, '_');
    }

    generateToken(roomId, userId) {
        // For production, this should be generated server-side
        // This is a simplified client-side token generation for demo purposes
        const payload = {
            iss: this.appID,
            exp: Math.floor(Date.now() / 1000) + 7200, // 2 hours from now
        };

        // In a real implementation, you'd call your backend to generate this token
        // For now, we'll use ZegoCloud's built-in token generation
        return window.ZegoUIKitPrebuilt.generateKitTokenForTest(
            this.appID,
            this.serverSecret,
            roomId,
            userId,
            this.userInfo.userName
        );
    }

    // Public method to manually retry if needed
    retryConnection() {
        this.retryAttempts = 0;
        this.init();
    }

    // Method to check if service is ready
    isServiceReady() {
        return this.isReady && this.checkConnectivity();
    }
}

// Initialize the video call manager when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.videoCallManager = new VideoCallManager();
        window.videoCallManagerInstance = window.videoCallManager; // For compatibility
    });
} else {
    window.videoCallManager = new VideoCallManager();
    window.videoCallManagerInstance = window.videoCallManager; // For compatibility
}

// Initialize ZegoCloud video call if we're on the videocall page
if (window.location.pathname === '/videocall') {
    document.addEventListener('DOMContentLoaded', function() {
        const urlParams = new URLSearchParams(window.location.search);
        const roomID = urlParams.get('roomID');
        const meetingName = urlParams.get('meetingName') || 'Video Call';
        let userName = urlParams.get('userName');
        let userID = urlParams.get('userID');

        // If no user info provided (shared link), prompt for name
        if (!userName || !userID) {
            userName = prompt('Enter your name to join the video call:') || 'Guest';
            userID = 'guest_' + Math.floor(Math.random() * 100000);
        }

        if (roomID && userName) {
            const appID = 382757591;
            const serverSecret = "a3a34788c4745c28fa325ebae4e8b0f5";

            // Update page title
            document.title = `${meetingName} - ProcessManager`;

            // Generate token for this call
            const token = ZegoUIKitPrebuilt.generateKitTokenForTest(
                appID,
                serverSecret,
                roomID,
                userID,
                userName
            );

            // Create ZegoCloud instance
            const zp = ZegoUIKitPrebuilt.create(token);

            // Join the call
            zp.joinRoom({
                container: document.querySelector('#video-call-container'),
                scenario: {
                    mode: ZegoUIKitPrebuilt.VideoConference,
                },
                showPreJoinView: true, // Show prejoin view for shared links
                showRoomTimer: true,
                showUserCount: true,
                maxUsers: 50, // Increased for shared meetings
                layout: "Auto",
                showLayoutButton: true,
                showScreenSharingButton: true,
                showAudioVideoSettingsButton: true,
                showTextChat: true,
                showUserList: true,
                lowerLeftNotification: {
                    showUserJoinAndLeave: true,
                    showTextChat: true,
                },
                branding: {
                    logoURL: "",
                },
                onJoinRoom: () => {
                    console.log('Joined video call room:', roomID);
                },
                onLeaveRoom: () => {
                    console.log('Left video call room:', roomID);
                    // Close the tab when leaving the call
                    window.close();
                },
                onUserJoin: (users) => {
                    console.log('Users joined:', users);
                },
                onUserLeave: (users) => {
                    console.log('Users left:', users);
                }
            });
        } else if (!roomID) {
            document.querySelector('#video-call-container').innerHTML = '<div class="text-center text-red-500 p-8">Invalid room ID</div>';
        } else {
            document.querySelector('#video-call-container').innerHTML = '<div class="text-center text-red-500 p-8">Name is required to join the call</div>';
        }
    });
}
