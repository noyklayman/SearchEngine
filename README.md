# 🔍 Mini Search Engine

URL: https://searchengine-6x32.onrender.com/

A distributed search engine project built with Java, Kafka, Elasticsearch, and web crawling techniques.

The system crawls websites, extracts content, processes pages asynchronously, and indexes data to provide fast and scalable search capabilities.

---

## 📌 Project Overview

Mini Search Engine demonstrates the core concepts behind modern search platforms, including web crawling, distributed processing, message queues, and full-text search.

Starting from a user-defined URL, the system recursively discovers web pages, extracts content, and stores searchable information inside Elasticsearch.

The architecture separates crawling, processing, and indexing into independent components, allowing the system to scale efficiently.

---

## ⚙️ Technologies Used

| Technology    | Purpose                        |
| ------------- | ------------------------------ |
| Java          | Core development               |
| Kafka         | Distributed message processing |
| Elasticsearch | Search and indexing engine     |
| Docker        | Containerized deployment       |
| REST APIs     | Search endpoints               |
| Web Crawling  | Content discovery              |
| DFS Algorithm | Recursive page traversal       |

---

## ✨ Features

* Recursive web crawling
* Link extraction from HTML pages
* Kafka-based asynchronous processing
* Content indexing with Elasticsearch
* Full-text search capabilities
* Scalable distributed architecture
* REST API integration
* Containerized deployment support

---

## 🔁 System Workflow

### 1. Seed URL Submission

The crawling process starts from a user-provided URL.

### 2. Web Crawling

The crawler:

* Downloads page content
* Extracts hyperlinks
* Discovers additional pages
* Traverses websites using DFS

### 3. Message Processing

Discovered URLs are published to Kafka topics and processed asynchronously.

### 4. Content Extraction

Workers retrieve pages, clean HTML content, and extract searchable information.

### 5. Search Indexing

Processed documents are indexed in Elasticsearch.

### 6. Search Requests

Users can query indexed content through REST API endpoints and receive relevant search results.

---

## 🚀 Running the Project

```bash
docker-compose up --build
```

The application will start all required services including Kafka, Elasticsearch, and the crawler components.

---

## 📚 Key Concepts Demonstrated

* Distributed Systems
* Web Crawling
* Kafka Message Queues
* Elasticsearch Indexing
* Full-Text Search
* Docker Containers
* REST API Design
* Scalable Backend Architecture

---

## 🔮 Future Improvements

* Page ranking algorithms
* Duplicate content detection
* NLP-based content enrichment
* Search result scoring
* Advanced analytics dashboard
