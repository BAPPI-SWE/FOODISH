# 🍔 Foodish 
### A Full-Stack, Hyper-Local Food Delivery Ecosystem

![Status](https://img.shields.io/badge/Status-Production%20Live-success)
![Users](https://img.shields.io/badge/Active%20Users-1000%2B-blue)
![Tech](https://img.shields.io/badge/Stack-Kotlin%20%7C%20Jetpack%20Compose%20%7C%20Firebase-orange)

**Foodish** is a production-grade, multi-platform food delivery ecosystem architected and developed solo. Designed specifically for **Daffodil Smart City (DSC)**, it solves the unique logistical challenges of campus dining by connecting students, restaurants, and delivery riders in real-time.

Currently serving **1,000+ active users** with daily transaction flows.

---

## 📱 The Ecosystem
The project consists of three distinct, native Android applications that communicate synchronously via a serverless backend:

1.  **User App:** For customers to browse menus, pre-order meals for breaks, or request instant delivery.
2.  **Partner App:** For restaurant owners to manage menus, view live order dashboards, and print kitchen tickets.
3.  **Rider App:** For delivery personnel to manage availability (Online/Offline), view order pools, and track earnings.



## 🛠 Tech Stack & Architecture

### **Android (Native)**
* **Language:** Kotlin (100%)
* **UI Framework:** Jetpack Compose (Declarative UI)
* **Architecture:** MVVM (Model-View-ViewModel) with Clean Architecture principles
* **Asynchronous Processing:** Kotlin Coroutines & Flow
* **Local Persistence:** Room Database (Hybrid cart system for offline access)
* **Dependency Injection:** Manual Dependency Injection / ViewModel Factory
* **Image Loading:** Coil

### **Backend (Firebase)**
* **Firestore:** Real-time NoSQL database serving as the single source of truth.
* **Authentication:** Google Sign-In & Email/Password.
* **Cloud Functions:** Serverless logic for triggering transactional push notifications.
* **Storage:** Managing user avatars and food images.

### **Tools & Integrations**
* **OneSignal:** Cross-app push notifications (e.g., Partner -> User "Food Ready").
* **AdMob:** Integrated Banner & Interstitial ads for monetization.
* **Git:** Version control.

---

## 🚀 Key Features

### 1. The "Smart" Ordering System (User App)
* **Pre-Order vs. Instant:** Students can schedule lunch for class breaks ("Pre-Order") or get immediate delivery to dorms ("Instant").
* **Real-Time Logic:** The "Instant Order" feature is **automatically disabled** globally if no Riders are marked "Online" in the Rider App.
* **Unified Cart:** A complex Room-based cart allowing items from multiple categories (Food, Grocery) to be managed simultaneously.
* **Location Filtering:** Custom logic to filter restaurants and offers based on specific campus buildings (DSC Academic Building, Dorms, etc.).

### 2. Operations Command Center (Partner App)
* **Live Dashboard:** Real-time badge counters for "Incoming" orders.
* **Batch Processing:** Filter orders by building/floor and "Accept All" to streamline kitchen workflow.
* **PDF Printing:** Generates professional kitchen tickets (KOT) directly from the app for physical order management.

### 3. Logistics Engine (Rider App)
* **Availability Switch:** A global toggle that dictates the entire platform's instant delivery capability.
* **Dynamic Order Pool:** Riders see a "First-come-First-serve" pool of pending orders filtered by their serviceable zones.

---

## 🧠 Engineering Challenges Solved

**Challenge:** Handling high concurrency when multiple riders try to accept the same order.
* **Solution:** Implemented Firestore Transactions to ensure atomic updates. If two riders tap "Accept" simultaneously, only the first request succeeds, preventing order duplication.

**Challenge:** Keeping the UI responsive while syncing data across 3 apps.
* **Solution:** Heavily utilized **Kotlin Flows** and Firestore Snapshots. The UI reacts instantly to database changes (e.g., order status updates) without manual refreshing, while heavy computations are offloaded to background threads using Coroutines.

**Challenge:** Offline Cart Persistence vs. Live Inventory.
* **Solution:** Built a hybrid repository pattern. The cart is stored locally in **Room** for speed and offline capability, but validates stock/prices against **Firestore** silently before Checkout to ensure data integrity.

---

## 👨‍💻 About the Developer

**Bappi** *Software Engineer | Competitive Programmer | Android Specialist*

I am a passionate builder who bridges the gap between algorithmic efficiency and product development. With a background in **Competitive Programming (340+ problems solved)**, I focus on writing optimized, scalable code.

* **LinkedIn:** [linkedin.com/in/bappi-swe](https://www.linkedin.com/in/bappi-swe)


---

## 📥 Download
The app is currently live for Daffodil Smart City residents.
https://play.google.com/store/apps/details?id=com.yumzy.userapp