# first-fit-memory-manager 🗃️

Memory managment simulator in Java. Project for **Operating Systems** module (2nd year Computer Science).

This memory management structure will include a simulation of:
- **malloc** (first-fit allocation)
- **free** + coalescing functionality
- **sbrk**

## Project Goals
- Simulate memory allocation (relatively close to the one present in C) using Java.
- Understand how metadata within memory blocks works.
- Implement a solid simulation and test it for edge cases

## How to run
_build_ and _run_ shell scripts are provided to compile and run the project.

1. cd into directory
2. `./build.sh`
3. `./run.sh`
  
## Overview

This memory allocator system follows the following structure:

![0aa286bdaf2f927587daa405c342bafa](https://github.com/ginesmoratalla/first-fit-memory-manager/assets/126341997/22ab6d46-052b-4c1d-b24e-432ff0b9b39d)

### 1. malloc(int size)
Request a chunk of memory of desired size (in bytes). will allocate a slightly bigger size for the chunk's metadata

### 2. free(int ptr)
Free a previously allocated memory chunk by its pointer and create a respective "free block"

### 3. sbrk(int size)
Simulate "requesting" memory to the OS if the initial chunk (8192 bytes) is exceeded, creating a new array of memory of size x = smallest power of 2 larger than size.

### 4. print / testing
The print function will run a series of tests with respective output structure and memory display, to understand and justify decisions for the implementation. Printed on terminal you will be able to read through the different tests with their descriptions.

### Relevant features
- malloc will find a free spot using first-fit method.
- Adjacent free blocks in the same memory array will coalesce/merge. Initial memory or sbrk generated arrays will not coalesce with each other.
