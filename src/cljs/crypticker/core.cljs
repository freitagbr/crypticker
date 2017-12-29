(ns crypticker.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async  :as async :refer (<! >! put! chan)]
            [reagent.core     :as r     :refer [atom]]
            [secretary.core   :as secretary :include-macros true]
            [accountant.core  :as accountant]
            [cljs-http.client :as http]
            [socket.io]))

;; -------------------------
;; Components

(defonce timer (atom (js/Date.)))
(defonce time-color (atom "#f34"))
(defonce time-updater
  (js/setInterval
    #(reset! timer (js/Date.)) 1000))
(defonce txs (atom '()))
(defonce tx-focus (atom {}))
(defonce tx-color (atom "#abc"))
(defonce socket (js/io "https://blockexplorer.com/"))

(defn subscribe
  "subscribes to the transaction channel"
  []
  (.emit socket "subscribe" "inv"))

(defn add-transaction
  "adds a transaction to the list of transactions"
  [data]
  (swap! txs (fn [txs tx]
               (cons tx (if (>= (count txs) 5)
                          (drop-last txs)
                          txs)))
         data))

(defn get-tx
  "gets info about a transaction"
  [txid]
  (go (let [response (<! (http/get (str "https://blockexplorer.com/api/tx/" txid)
                                   {:with-credentials? false}))]
        (reset! tx-focus (:body response)))))

(defn clock []
  (let [time-str (-> @timer .toTimeString (clojure.string/split " ") first)]
    [:div.example-clock
     {:style {:color @time-color}}
     time-str]))

(defn transactions []
  [:table
   [:thead
    [:tr
     [:th "Hash"] [:th "Value"]]]
   (into [:tbody]
         (for [tx @txs
               :let [txid (.-txid tx)
                     value (.-valueOut tx)]]
           ^{:key txid}
           [:tr.tx {:on-click #(get-tx txid)}
            [:td
             [:pre
              {:style {:color @tx-color}}
              (subs txid 0 8)]]
            [:td
             [:pre value]]]))])

(defn focused-tx []
  (if (> (count @tx-focus) 0)
    (let [{:keys [valueIn valueOut txid size fees blockheight]} @tx-focus]
      [:div
       [:h4 "Value In"]
       [:div valueIn]
       [:h4 "Value Out"]
       [:div valueOut]
       [:h4 "Hash"]
       [:pre txid]
       [:h4 "Size"]
       [:pre (str size " bytes")]
       [:h4 "Fees"]
       [:div fees]
       [:h4 "Block Height"]
       [:div blockheight]])
    nil))


;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to crypticker"]
   [:div
    [:h3 "The time is now:"]
    [clock]
    [transactions]
    [focused-tx]]
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
