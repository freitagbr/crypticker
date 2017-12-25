(ns crypticker.core
  (:require [cljs.core.async  :as async :refer (<! >! put! chan)]
            [reagent.core     :as r     :refer [atom]]
            [secretary.core   :as secretary :include-macros true]
            [accountant.core  :as accountant]
            [socket.io]))

;; -------------------------
;; Components

(defonce timer (atom (js/Date.)))
(defonce time-color (atom "#f34"))
(defonce time-updater
  (js/setInterval
    #(reset! timer (js/Date.)) 1000))
(defonce txs (atom '()))
(defonce tx-color (atom "#abc"))
(defonce socket (js/io "https://blockexplorer.com/"))

(defn subscribe
  "subscribes to the transaction channel"
  []
  (.emit socket "subscribe" "inv"))

(defn add-transaction
  "adds a transaction to the list of transactions"
  [data]
  (do
    (js/console.log data)
    (swap! txs (fn [txs tx]
                 (cons tx (if (>= (count txs) 5)
                            (drop-last txs)
                            txs)))
           data)))

(defn clock []
  (let [time-str (-> @timer .toTimeString (clojure.string/split " ") first)]
    [:div.example-clock
     {:style {:color @time-color}}
     time-str]))

(defn transactions []
  (into [:div]
        (for [tx @txs
              :let [txid (.-txid tx)
                    value (.-valueOut tx)]]
          ^{:key txid}
          [:div.tx
           [:pre
            {:style {:color @tx-color}}
            (subs txid 0 8)]
           [:pre value]])))

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to crypticker"]
   [:div
    [:h3 "The time is now:"]
    [clock]
    [transactions]]
   [:div [:a {:href "/about"} "go to about page"]]])

(defn about-page []
  [:div [:h2 "About crypticker"]
   [:div [:a {:href "/"} "go to the home page"]]])

;; -------------------------
;; Routes

(def page (atom #'home-page))

(defn current-page []
  [:div [@page]])

(secretary/defroute "/" []
  (reset! page #'home-page))

(secretary/defroute "/about" []
  (reset! page #'about-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (r/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)

  ; set up socket.io connection to blockexplorer
  (doto socket
    (.on "connect" subscribe)
    (.on "tx" add-transaction))

  (mount-root))
