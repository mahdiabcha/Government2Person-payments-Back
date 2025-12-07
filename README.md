## Vue d’ensemble

Ce repository contient une solution **microservices** pour la gestion des aides financières publiques **Government-to-Person (G2P)**.  
Le système prend en charge le cycle complet :  
**inscription → enrôlement → éligibilité → cycles → droits de paiement → lots → exécution bancaire → notifications**. 

Comme pour le front, la solution fait suite à un PoC OpenG2P jugé trop lourd et difficile à maîtriser, d’où le choix d’une architecture interne plus claire et flexible. 

---

## Architecture

- **Microservices autonomes** : 1 service par fonctionnalité métier.
- **API Gateway** : point d’entrée unique (auth, autorisation, routage).
- **Communication** : HTTP/REST (synchrone) + AMQP (asynchrone).
- **Données** : PostgreSQL par service.
- **Conteneurisation** : Docker (1 Dockerfile/service).
- **Orchestration** : Docker Compose.

---

## Microservices

- **gateway-service**  
  Point d’entrée `/api/**` : routage et centralisation de la sécurité.

- **auth-service**  
  Gestion des comptes et rôles (citoyen, administrateur) + JWT.

- **program-catalog-service**  
  CRUD programmes d’aide + règles d’éligibilité.

- **profile-service**  
  Stockage et gestion des profils bénéficiaires.

- **enrollment-service**  
  Soumission et traitement des demandes d’enrôlement (workflow admin).

- **payment-service**  
  Gestion des droits de paiement, cycles, lots (batchs) et statuts de transactions.

- **mockbank-service**  
  Banque simulée pour tests/démo.

- **notifications-service**  
  Notifications liées aux enrôlements et paiements. 

---

## Acteurs & flux métier

### Citoyen
1. Crée un compte et complète son profil.
2. Vérifie son éligibilité.
3. Demande à s’enrôler à un programme.
4. Suit le statut de sa demande.
5. Reçoit paiements et notifications.

### Administrateur
1. Crée/active programmes et règles d’éligibilité.
2. Gère les cycles d’aide.
3. Approuve/rejette les enrôlements.
4. Prépare et approuve les droits de paiement.
5. Crée et dispatch les lots de paiement. 

---

## Exigences non fonctionnelles
- **Sécurité** : mots de passe chiffrés + jetons (données financières sensibles).
- **Performance** : traitements rapides.
- **Maintenabilité & scalabilité** : clean code, services indépendants.
- **Ergonomie globale du système** (via gateway + front).

---

## Stack 
- Java 17+
- Spring Boot / Spring Cloud
- Maven wrapper (`./mvnw`)
- PostgreSQL
- AMQP (RabbitMQ ou équivalent)
- Docker / Docker Compose

---

## Prérequis
- JDK 17
- Docker + Docker Compose v2
- Maven (optionnel si tu utilises le wrapper)

---

