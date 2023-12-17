/* Operating Systems (LZSCC.211) Coursework 2 -Memory management
 * 
 * Author (student id): 38879816
 * December 2023
*/

// Imports (libraries)
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Random;
import java.lang.Math;

// Code
public class MemoryMgmt {
    /* 
     * Variable declaration
     * 
     * Keep track of used memory as MemoryBlocks.
     * MemoryBlocks will have MemoryChunks inside (MemoryChunks will be the metadata for MemoryBlocks).
     * It is a 32-bit system simulation, so each memory chunk reserved for pointers
     * or length trackers will by 4 bytes (32 bits).
     * 
     */
    private ArrayList<MemoryBlock> memoryList = new ArrayList<>();
    private ArrayList<ArrayList<MemoryBlock>> list_of_memory_lists = new ArrayList<>();
    private final int MAX_RANDOM = 100000000;
    /*
     * Keep track of free memory
     * Different free lists where we keep track of the available memory in different size ranges
     * e.g., free_list_32 -> free list of slots of blocks that are 32 bytes or smaller.
     * e.g., free_list_8192 -> free slots of blocks that are 1024-8192+ bytes long (the biggest list will store sizes bigger than 8192)
     * 
     * In this implementation, free blocks are stored both in free lists and in main memory.
     * There won't be 2 equal free blocks, but rather the same free block reference in both lists.
     * Free lists will be used to find free blocks when calling malloc, and the Memory lists will be iterated through
     * when updating the pointers of these free blocks and coalescing adjacent blocks.
     */
    private ArrayList<MemoryBlock> free_list_32 = new ArrayList<MemoryBlock>();
    private ArrayList<MemoryBlock> free_list_64 = new ArrayList<MemoryBlock>();
    private ArrayList<MemoryBlock> free_list_128 = new ArrayList<MemoryBlock>();
    private ArrayList<MemoryBlock> free_list_512 = new ArrayList<MemoryBlock>();
    private ArrayList<MemoryBlock> free_list_1024 = new ArrayList<MemoryBlock>();
    private ArrayList<MemoryBlock> free_list_8192 = new ArrayList<MemoryBlock>();

    private ArrayList<ArrayList<MemoryBlock>> list_of_free_lists = new ArrayList<ArrayList<MemoryBlock>>();

    // Constructor: we will add the initial free lists and memorylist (our memory starter)
    public MemoryMgmt() {

        list_of_free_lists.add(free_list_32);
        list_of_free_lists.add(free_list_64);
        list_of_free_lists.add(free_list_128);
        list_of_free_lists.add(free_list_512);
        list_of_free_lists.add(free_list_1024);
        list_of_free_lists.add(free_list_8192);
        // MemoryList will be the starter memory for all tests (8KB)
        list_of_memory_lists.add(memoryList);

    }
    // Main Method (will call the tests to stdout)
    public static void main(String[] args) {
        MemoryMgmt memeMgmt = new MemoryMgmt(); // Create instance to acess non-static methods
        System.out.println("\r\n===========================================================================================");
        System.out.println("                                Operating systems (LZSCC211)");
        System.out.println("                            Coursework 2: Memory Management");
        System.out.println("\r===========================================================================================\n");
        memeMgmt.print();
    }

    // Memory management methods
    public int malloc(int size) {
        
        System.out.print("\nRequesting " + size + " bytes of memory... ");  
        /*
         * According to (Stack Overflow, n.d.) in C, malloc lets you call for negative size,
         * but, since malloc takes the value given and converts it to its unsigned binary numberm
         * it will be converted to a very big positive number by converting it to it's unsigned binary
         * and then back to size (now big+ instead of negative).
         * 
         * Stack Overflow. (n.d.). C - Recoding malloc() how to detect a negative size used in the call? [online]
         * Available at: 
         * https://stackoverflow.com/questions/28500263/c-recoding-malloc-how-to-detect-a-negative-size-used-in-the-call 
         * [Accessed 4 Dec. 2023].
         * 
         * I was not able to find any more "formal" documentation on this.
         * 
         * As this malloc takes int as a parameter, it won't be possible to convert the negative number to it's
         * unsigned binary version and then back to int, because this new converted number won't fit in an int,
         * therefore, a random big number will be created instead simulating this behaviour.
         * 
         * MAX_RANDOM is within a range that allows to be stored as an int.
         */
        if(size < 0) {
            System.err.println("Exception in malloc. Allocating negative chunk. Might cause undefined behaviour.");
            Random random = new Random();
            size = Math.abs(random.nextInt() % MAX_RANDOM);                      // Sometimes turns out negative, so make it positive all the time
        } 
        int actualsize = (size*8);                                               // Size will be in bits, so from here we convert malloc bytes to bits (might make it more complicated but more precise)

        // Find your first free block (block with smallest adress) and the memory list where it is stored
        MemoryBlock free_block = getFirstFreeBlock();   
        ArrayList<MemoryBlock> current_memory_list = returnMemoryList(free_block.get_length().chunk_adress);
        // Return with the adress of your newly allocated block from mallocCall (function)
        int return_type = mallocCall(free_block, actualsize, current_memory_list);
        if(return_type != -1) return return_type;
        /*
         * This part was reached if there is no memory left to allocate block of size "size",
         * in memory, so we call sbrk.
         * 
         * Similar process to the one above, now with the new memory list and adress as a reference
        */
        System.out.println("");
        sbrkReturn sbrk_return = sbrk(size);
        free_block = getFirstFreeBlock();
        System.out.println("New malloc(" + size + ") block header will start at address " + hexConverter(sbrk_return.getPointer()/8));
        return_type = mallocCall(free_block, actualsize, sbrk_return.getArrayList());
        if(return_type != -1) return return_type;

        // This code should be unreachable unless undefined behaviour happens.
        System.out.println("Exception in malloc. sbrk could not allocate desired chunk. Exiting.");
        return -1;
    }
    //_____________________________________________________________________________________________________________________________________________//

    public void free(int ptr) {
        try {
            System.out.print("\nFreeing pointer " + hexConverter(ptr/8) + "... ");

            // Iterate through memory lists to see if we find such pointer
            for(ArrayList<MemoryBlock> list : list_of_memory_lists) {
                for(MemoryBlock block : list) {
                    if(!block.get_length().free_chunk && block.get_previous_length().chunk_adress + 32 == ptr) {
                        
                        int adress_1 = block.get_previous_length().chunk_adress;
                        int adress_2 = block.get_length().chunk_adress;
                        // Create a new free block starting where the freed malloc block header starts and ends
                        MemoryBlock new_free_block = createFreeBlock(adress_1, adress_2, block.get_actual_chunk().chunk_size + 64);
                        // Find the free list that best fits this new free block by size range (NOT best fit allocation method)
                        ArrayList<MemoryBlock> freelist = bestFit(block.get_actual_chunk().chunk_size/8);
                        // Add free block in free list and in main memory
                        freelist.add(new_free_block);
                        list.add(new_free_block);
                        // Remove the desired memory block from main memory
                        list.remove(block);

                        // Deal with coalescing free blocks to other free blocks around them
                        coalescing();
                        System.out.println("memory freed.");
                        return;
                    }
                }
            }
            System.err.println("Exception triggered in free (pointer not found). Exiting.");

        } catch (Exception e) {
            System.out.println("Unexpected exception triggered in free. Exiting.");
        }
    }
    //_____________________________________________________________________________________________________________________________________________//

    public sbrkReturn sbrk(int size) {
        /*
         * Return type sbrkReturn is a class that returns both an array where the block is going to be stored (new sbrk memory array)
         * and an integer that is the start of said sbrk allocated chunk.
         * sbrk just generates the array and pointer, the actual block will be allocated back in malloc.
         * 
         * This new sbrk array will start right after the previous memory array that couldn't allocate the chunk,
         * although, free blocks from this sbrk array will never be merged with the ones in main memory or
         * other sbrk arrays (if any) due to the distance of their headers and rears.
         * 
         * For this simulation, it was mentioned that issues regarding concurrency should not be taken into account,
         * therefore, it is assumed that space in memory right after our previous memory block will not be requested anywhere else.
         * This is why a new sbrk block will be allocated next to the previous memory block.
         */
        System.out.print("Memory limit exceeded, requesting further memory blocks...");
        // Get the smallest power of 2 larger than size
        double pow = 2;
        size += 8;                                                              // Adding the header and tail chunks to be able to allocate blocks that are close to power
        double size_ = (double) size;
        int times = 1;
        while (pow <= size_) {
            pow = Math.pow(2, times);
            times++;
        }
        int power = (int) pow;
        /*
         * New sbrk array as a memory list.
         * Free blocks inside this memory list will NOT be merged with blocks from any other memory list (main memory or other sbrk arrays)
         * due to the distance this one has with other memory arrays (not small enough to coalesce)
         */
        ArrayList<MemoryBlock> new_memory_list = new ArrayList<>();

        // Get last memory list's rear guard's adress (this adress + 32 will be the begginning of the sbrk memory array)
        ArrayList<MemoryBlock> old_memory_list;
        MemoryBlock rear = null;
        for(ArrayList<MemoryBlock> list : list_of_memory_lists) {
            old_memory_list = list;
            // The rear guard will always be the second added block in any memory list
            rear  = old_memory_list.get(1);
        }

        // Set up new list for allocating chunks. 128 bits is the minimum size of a free block (its header and prev next chunks make up for that space)
        MemoryBlock head = setupMemoryHead(rear.get_length().chunk_adress + 32, power*8, new_memory_list);
        list_of_memory_lists.add(new_memory_list);
        // Set up the specific class for the sbrk return type, with our pointer to new adress and new memory list
        sbrkReturn returntype = new sbrkReturn(head.get_length().chunk_adress + 32, new_memory_list);
        return returntype;
    }
    //_____________________________________________________________________________________________________________________________________________//

    public void print() {
        // Tests will run separately to not interfere with other test calls or similar variable names.
        test1();
        test2();
        test3();
        test4();
        test5();
        test6();
        test7(); 
        test8();
        test9();
        test10();
        test11();
    }
    public void test1() {
        testHeader("", "Running test 1. --> Coursework Documentation test (a) ...");

        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int first = malloc(28);
        int adress = storeString(first, "Operating Systems");
        String retreived_a = retreiveString(adress);
        System.out.println("Retrieved string: '" + retreived_a + "'");
        free(first);
    }
    public void test2() {
        testHeader("", "Running test 2. --> Coursework Documentation test (b) ...");

        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int first = malloc(28);
        int second = malloc(1024);
        int third = malloc(28);
        free(second);
        int fourth = malloc(512);
        // Call printMemory() at any given time to see the current memory display
        printMemory();                                                                                

        free(first);
        free(third);
        free(fourth);
    }
    public void test3() {
        testHeader("", "Running test 3. --> Coursework Documentation test (c) ...");
        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int first = malloc(7168);
        int second = malloc(1024);

        free(first);
        free(second);
        printMemory();
    }
    public void test4() {
        testHeader("", "Running test 4. --> Coursework Documentation test (d) ...");

        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int d_first = malloc(1024);
        int d_second = malloc(28);
        free(d_second);
        free(d_second);
        printMemory();
    }
    public void test5() {
        testHeader("Test goal: Coalescing functionality (should merge any adjacent free blocks instantly)\nmalloc(40); malloc(128); free(40); malloc(512); free(128); malloc(60)\n\nExpected outuput:\n- malloc(512) starts after malloc(128)\n- malloc(60) starts where malloc(40) initially was.", "Running test 5. --> Personal test...\n");

        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int first = malloc(40);
        int second = malloc(128);
        free(first);
        int third = malloc(512);
        free(second);
        int fourth = malloc(60);
        printMemory();
    }
    public void test6() {
        testHeader("Test goal: malloc freeing another reference (double freeing)\nmalloc(28); malloc(1024); free(28); malloc(12); free(28)\n\nExpected outuput:\n- Last free(28) will free(12) because it was allocated in the same pointer.", "Running test 6. --> Personal test...\n");
        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int first = malloc(28);
        int second = malloc(1024);
        free(first);
        int third = malloc(12);
        free(first);
    }
    public void test7() {
        testHeader("Test goal: Allocate 3 strings in 1 memory block\nmalloc(10); storeString('Dogs'); storeString('Test String 2'); storeString('Third string')\n\nExpected outuput:\n- storeString('Test String 2') will not store the full string, as it won't have enough space in the block.\n- retrieveString('Test String 2') will not return the full string.\n- When printed, it will come as 'null' (will not find \\0 terminator).\n- Allocating a third string will not be possible, as there will be no space left.", "Running test 7. --> Personal test...\n");
        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int first = malloc(10);
        int address_1 = storeString(first, "Dogs");
        String string_1 = retreiveString(address_1);
        System.out.println("Retrieved string: '" + string_1 + "'\n\n");
        // Second string
        int address_2 = storeString(first, "Test String 2");
        String string_2 = retreiveString(address_2);
        System.out.println("\nRetrieved string: '" + string_2 + "'\n\n");

        int adr = storeString(first, "Third string");
        retreiveString(adr);
        free(first);
    }
    public void test8() {
        testHeader("Test goal: Test multiple sbrk calls\nmalloc(8000); malloc(200); malloc(1023); malloc(3000)\n\nExpected outuput:\n- Every malloc call in this test (except the first) should request for more memory via sbrk\n- Memory display should have 1 Main Memory 'array' and 3 sbrk 'arrays.'", "Running test 8. --> Personal test...\n");
        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int first = malloc(8000);
        int second = malloc(200);
        int third = malloc(1023);
        int fourth = malloc(3000);
        printMemory();
        free(fourth);
        free(third);
        free(first);
        free(second);
    }
    public void test9() {

        testHeader("Test goal: store 2 int values in a malloc block\n- Allocated int should take 4 bytes (32-bit architecture), the int will be stored accross these bytes as a Two's Complement binary string", "Running test 9. --> Personal test...\n");
        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int first = malloc(64);
        int ptr = storeInt(first, -40);
        int my_int = retrieveInt(ptr);
        System.out.println("Retrieved integer: " + my_int + "\n");
        int ptr_2 = storeInt(first, 5500);
        int my_int_2 = retrieveInt(ptr_2);
        System.out.println("Second retireved integer: " + my_int_2);
    }
    public void test10() {
        testHeader("Test goal: sbrk arrays are NOT requested by the OS & Proving not-coalescing between sbrk arrays\n\nExpected output:\n- After freeing memory that was allocated after calling sbrk, we will still be able to use it,\n it should not be reclaimed by the OS.\n- malloc(1000) should start in the sbrk memory instead of main memory.\n- malloc(100) will be allocated in main memory, proving that free(2000) doesn't\n make sbrk array and main memory coalesce", "Running test 10. --> Personal test...\n");
        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int first = malloc(8000);
        int second =  malloc(2000);
        free(second);
        int thrid = malloc(100);
        int fourth = malloc(1000);
        printMemory();
        free(first);
        free(thrid);
        free(fourth);
    }
    public void test11() {
        testHeader("Test goal: allocating a negative number\n\nExpected output:\n- A very big chunk of memory will be allocated instead of a negative one, following\n the explanation from negative allocation in malloc.", "Running test 11. --> Personal test...\n");
        MemoryBlock head = reset();
        System.out.println("HEAD Pointer: " + hexConverter(head.get_previous_length().chunk_adress/8));
        int first = malloc(-7);
        printMemory();
        free(first);
    }

    public void testHeader(String content, String test_type) {
        System.out.println("___________________________________________________________________________________________");
        System.out.println("___________________________________________________________________________________________\n");
        System.out.println(test_type);
        System.err.println(content);
        System.out.println("___________________________________________________________________________________________\n");
    }

    // EXTRA METHODS (used to complete the methods above)
    //_____________________________________________________________________________________________________________________________________________//
    // Interface method to see the current memory display
    public void printMemory() {
        System.out.println("\n===========================================================================================");
        System.out.println("|                                  Current Memory Display                                 |");
        System.out.println("===========================================================================================");

        int j = 0;
        for(ArrayList<MemoryBlock> memory_list : list_of_memory_lists) {
            if(j == 0) {
                System.out.println("\n----------------------------------- System Memory Block -----------------------------------\n");
            } else {
                System.out.println("\n----------------------------- sbrk Generated Memory Block " + j + " -------------------------------\n");
            }

            int i = 0;
            for (MemoryBlock block : memory_list) {
                if(i == 0) {
                    System.out.println("HEAD Pointer: " + hexConverter(block.get_previous_length().chunk_adress/8));
                    i++; 
                }
                if (!block.guard  && i > 0 && !block.get_length().free_chunk){
                    System.out.println("\nBlock " + i + " [Pointer: " + hexConverter((block.get_previous_length().chunk_adress + 32)/8) + ";  size: " + block.get_actual_chunk().chunk_size/8 + " bytes;  Header adress: " + hexConverter(block.get_previous_length().chunk_adress/8) + ";  Tail adress: " + hexConverter(block.get_length().chunk_adress/8) + "]");
                    i++;
                }
            }
            j++;
            System.out.println("\nREAR Pointer: " + hexConverter(memory_list.get(1).get_previous_length().chunk_adress/8));
        }
        System.out.println("\n===========================================================================================");
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Convert an integer (adress) in decimal to hexadecimal (Used to print adress in proper format).
    public String hexConverter(Integer dec_to_hex) {
        String hexAdress = Integer.toHexString(dec_to_hex).toUpperCase();                             // Built-in java method to convert int to hex string
        while (hexAdress.length() < 4) {                                                              // Add corresponding 0s for hex number to be of length 4
            hexAdress = "0" + hexAdress;
        }
        hexAdress = "0x" + hexAdress;
        return hexAdress;
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Sets up an initial head for an 'array' of memory, with its initial guards (metadata)
    public MemoryBlock setupMemoryHead(int adr, int availableMemory, ArrayList<MemoryBlock> array) {

        // Setting up the Head Guard
        MemoryChunk head_length = new MemoryChunk(adr, false, 32, null, null);
        adr += head_length.chunk_size;
        availableMemory -= head_length.chunk_size;

        MemoryBlock headGuard = new MemoryBlock(head_length, head_length, head_length, true);
        /*
         * Setting up the tail Guard (-32 because the guard's adress does not begin right at the end, but 4 byes before)
         * e.g., In main memory, rear guard goes from 8188 - 8192 adress in bytes (0x1FFC - 0x2000).
         */
        MemoryChunk rear = new MemoryChunk(availableMemory + adr - 32, false, 32 , null, null);
        availableMemory -= rear.chunk_size;
        MemoryBlock rearGuard = new MemoryBlock(rear, rear, rear, true);

        // Add the first free chunk to a fitting free list
        MemoryBlock freeblock = createFreeBlock(adr, rear.chunk_adress - 32, availableMemory);
        ArrayList<MemoryBlock> first_free_list = bestFit(availableMemory);

        first_free_list.add(freeblock);
        array.add(headGuard);
        array.add(rearGuard);
        array.add(freeblock);
        return headGuard;

    }
    //_____________________________________________________________________________________________________________________________________________//
    // MemoryBlocks are either free blocks or malloc blocks (each with different metadata -> constructors)
    private class MemoryBlock {

        private MemoryChunk previous_length;
        private MemoryChunk actual_chunk;
        private MemoryChunk length;

        private MemoryChunk previous;
        private MemoryChunk next;

        private boolean guard;

        // Free Memory BLock Constructor
        public MemoryBlock(MemoryChunk length, MemoryChunk previous, MemoryChunk next, MemoryChunk previous_length, MemoryChunk actual_chunk, boolean guard) {

            this.previous_length = previous_length;
            this.actual_chunk = actual_chunk;
            this.length = length;

            this.next = next;
            this.previous = previous;

            this.guard = guard;
        }

        // Malloc Memory Block Constructor
        public MemoryBlock(MemoryChunk previous_length, MemoryChunk length, MemoryChunk actual_chunk, boolean guard) {

            this.previous_length = previous_length;
            this.length = length;
            this.actual_chunk = actual_chunk;
            this.guard = guard;
        }

        // Getters
        public MemoryChunk get_previous_length() {
            return this.previous_length;
        }
        public MemoryChunk get_length() {
            return this.length;
        }
        public MemoryChunk get_actual_chunk() {
            return this.actual_chunk;
        }
        public MemoryChunk get_next() {
            return this.next;
        }
        public MemoryChunk get_previous() {
            return this.previous;
        }
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Chunk is the data type for the metadata on a MemoryBlock
    private class MemoryChunk {

        private Integer chunk_adress;
        private Integer chunk_size;
        private boolean free_chunk;
        private Data[] data;
        private Integer pointer;

        public MemoryChunk(Integer chunk_adress, boolean free_chunk, Integer chunk_size, Data[] data, Integer pointer) {

            this.chunk_adress = chunk_adress;
            this.chunk_size = chunk_size;
            this.free_chunk = free_chunk;
            this.data = data;
            this.pointer = pointer;
        }
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Check free lists to find a list which has 'size' in range
    public ArrayList<MemoryBlock> bestFit(int size) {
        if (size <= 32) {
            return free_list_32;
        } if (size <= 64) {
            return free_list_64;
        } if (size <= 128) {
            return free_list_128;
        } if (size <= 512) {
            return free_list_512;
        } if (size <= 1024) {
            return free_list_1024;
        } else {
            return free_list_8192;
        }
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Find a free list if it contains a certain block
    public ArrayList<MemoryBlock> findList(MemoryBlock size) {
        if (free_list_32.contains(size)) {
            return free_list_32;
        } else if (free_list_64.contains(size)) {
            return free_list_64;
        } else if (free_list_128.contains(size)) {
            return free_list_128;
        } else if (free_list_512.contains(size)) {
            return free_list_512;
        } else if (free_list_1024.contains(size)) {
            return free_list_1024;
        } else if (free_list_8192.contains(size)) {
            return free_list_8192;
        } else {
            return null;
        }
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Find an appropiate free list according to a given value which needs to fall within range
    public int currentFreelistSize(ArrayList<MemoryBlock> size) {
        if (size == free_list_32) {
            return 32;
        } else if (size == free_list_64) {
            return 64;
        } else if (size == free_list_128) {
            return 128;
        } else if (size == free_list_512) {
            return 512;
        } else if (size == free_list_1024) {
            return 1024;
        } else {
            return 8192;
        }
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Create a Free Block when calling free or when creating a new array
    private MemoryBlock createFreeBlock(int this_adress, int last_adress, int memory) {

        MemoryChunk length = new MemoryChunk(this_adress, true, 32, null, null);
        this_adress += length.chunk_size;
        memory -= length.chunk_size;

        MemoryChunk previous = new MemoryChunk(this_adress, false, 32, null, null);
        this_adress += previous.chunk_size;
        memory -= previous.chunk_size;

        MemoryChunk next = new MemoryChunk(0, false, 32, null, null);
        this_adress += next.chunk_size;
        memory -= next.chunk_size;

        MemoryChunk previousLength = new MemoryChunk(last_adress, true, 32, null, null);
        memory -= previousLength.chunk_size;

        MemoryChunk actual_chunk = new MemoryChunk(this_adress, true, memory, null, null);

        MemoryBlock block = new MemoryBlock(length, previous, next, previousLength, actual_chunk, false);

        return block;
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Merge adjacent Free Blocks
    public void coalescing() {

        // Copy all free blocks into a copy list (collections_array)
        ArrayList<MemoryBlock> collections_array = new ArrayList<>();
        for(ArrayList<MemoryBlock> list : list_of_memory_lists) {
            for(MemoryBlock min : list) {
                if(min.get_length().free_chunk) {
                    collections_array.add(min);
                }
            }
        }
        // Sort collections_array by adress (the smallest adress the first)
        collections_array = sortArray(collections_array);
        // Update the pointers of the real free blocks (not in the copy array)
        for(int i = 0; i < collections_array.size(); i++) {
            MemoryBlock curr = getBlock(collections_array.get(i));
            if(i+1 != collections_array.size()) {
                MemoryBlock next = getBlock(collections_array.get(i + 1));
                curr.get_next().pointer = next.get_length().chunk_adress;
            }
            if(i > 0) {
                MemoryBlock prev = getBlock(collections_array.get(i-1));
                curr.get_previous().pointer = prev.get_length().chunk_adress;
            }
        }
        /*
         * Find adjacent blocks in the sorted arraylist (collections_array) of free blocks and merge them as one.
         * Done with iterator to avoid ConcurrentModificationException (modifying a list while iterating through it)
         */
        ListIterator<MemoryBlock> iterator = collections_array.listIterator();
        while (iterator.hasNext()) {
            MemoryBlock block1 = iterator.next();
            if(iterator.hasNext()) {
                MemoryBlock block2 = iterator.next();
                if(block1.get_previous_length().chunk_adress + 32 == block2.get_length().chunk_adress) {
                    MemoryBlock toMerge1 = getBlock(block1);
                    MemoryBlock toMerge2 = getBlock(block2);
                    ArrayList<MemoryBlock> memory_list = returnMemoryList(block1.get_length().chunk_adress);
                    MemoryBlock newBLock = mergeFreeBlocks(toMerge1, toMerge2, memory_list);
                    iterator.previous();
                    iterator.set(newBLock);
                } else {
                    iterator.previous();
                }
            }
        }
    }
    //_____________________________________________________________________________________________________________________________________________//

    public MemoryBlock findFreeBlock(int pointer) {
        // Finds a free block given a pointer that should match the free blocks header chunk
        for(ArrayList<MemoryBlock> list : list_of_free_lists) {
            for(MemoryBlock free_block : list) {
                if(free_block.get_length().chunk_adress == pointer) {
                    return free_block;
                }
            }
        }
        return null;
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Resets memory and re-generates first initial block and guards (called after every test usually)
    public MemoryBlock reset() {

        // Using Iterator to avoid ConcurrentModificationException (removing items from a list while iterating through it)
        Iterator<ArrayList<MemoryBlock>> lists_iterator = list_of_memory_lists.iterator();

        while(lists_iterator.hasNext()) {

            ArrayList<MemoryBlock> memory_lists = lists_iterator.next();
            Iterator<MemoryBlock> block_iterator = memory_lists.iterator();
            
            while(block_iterator.hasNext()) {
                block_iterator.next();
                block_iterator.remove();
            }
            if(list_of_memory_lists.get(0) != memory_lists) {
                lists_iterator.remove();
            }
        }
        lists_iterator = list_of_free_lists.iterator();
        while(lists_iterator.hasNext()) {
            ArrayList<MemoryBlock> free_lists = lists_iterator.next();
            Iterator<MemoryBlock> block_iterator = free_lists.iterator();
            while(block_iterator.hasNext()) {
                block_iterator.next();
                block_iterator.remove();
            }
        }
        // Create memory array again
        return setupMemoryHead(0, 8192*8, memoryList);
    }
    //_____________________________________________________________________________________________________________________________________________//

    public MemoryBlock mergeFreeBlocks(MemoryBlock free_block, MemoryBlock second_free_block, ArrayList<MemoryBlock> memory_list) {
        /*
         * Method that is called only when two adjacent free blocks are found.
         * Updates the pointers and the size of the first block (free_block) and removes the second_free_block
         * making a bigger first block.
         * Will also update its free_list in case it needs to be added to a bigger free list
         */
        memory_list.remove(free_block);
        memory_list.remove(second_free_block);

        ArrayList<MemoryBlock> list_to_remove_1 = findList(free_block);
        list_to_remove_1.remove(free_block);

        ArrayList<MemoryBlock> findList = findList(second_free_block);
        findList.remove(second_free_block);

        free_block.get_next().pointer = second_free_block.get_next().pointer;
        free_block.get_previous_length().chunk_adress = second_free_block.get_previous_length().chunk_adress;
        free_block.get_actual_chunk().chunk_size += second_free_block.get_actual_chunk().chunk_size + 96;
        
        ArrayList<MemoryBlock> desiredList = bestFit(free_block.get_actual_chunk().chunk_size/8);
        desiredList.add(free_block);
        memory_list.add(free_block);

        return free_block;
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Class used as return type for sbrk (contains both the a pointer to where our block will be allocated and the new array in which it will be)
    private class sbrkReturn {
        private Integer new_pointer;
        private ArrayList<MemoryBlock> newMemList;

        public sbrkReturn(Integer new_pointer, ArrayList<MemoryBlock> newMemList) {
            this.new_pointer = new_pointer;
            this.newMemList = newMemList;
        }

        public ArrayList<MemoryBlock> getArrayList() {
            return this.newMemList;
        }
        public Integer getPointer() {
            return this.new_pointer;
        }
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Used in coalescing(). Sorts an array by header address size
    public ArrayList<MemoryBlock> sortArray(ArrayList<MemoryBlock> arraylist) {

        int n = arraylist.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (arraylist.get(j).get_length().chunk_adress > arraylist.get(j + 1).get_length().chunk_adress) {
                    MemoryBlock temporal_block = arraylist.get(j);
                    arraylist.set(j, arraylist.get(j + 1));
                    arraylist.set(j + 1, temporal_block);
                }
            }
        }
        return arraylist;
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Returns real block reference that matches passed parameter (passing copy list block)
    public MemoryBlock getBlock(MemoryBlock block) {
        for(ArrayList<MemoryBlock> list : list_of_memory_lists) {
            for(MemoryBlock blockfinder : list) {
                if(blockfinder.get_length().chunk_adress == block.get_length().chunk_adress) {
                    return blockfinder;
                }
            }
        }
        return null;
    }
    public MemoryBlock getMemoryBlock(int address) {
        for(ArrayList<MemoryBlock> list : list_of_memory_lists) {
            for(MemoryBlock blockfinder : list) {
                if(blockfinder.get_previous_length().chunk_adress == address - 32) {
                    return blockfinder;
                }
            }
        }
        return null;
    }
    // Returns a memory block given the adress of data (e.g., string)
    public MemoryBlock getBlockByData(int adress) {
        MemoryBlock mem_block = null;
        for(ArrayList<MemoryBlock> list : list_of_memory_lists) {
            for(MemoryBlock block : list) {
                Data[] data = block.get_actual_chunk().data;
                if(data != null) {
                    for(int i = 0; i < data.length; i++) {
                        Data current_data = data[i];
                        if(current_data != null && current_data.adress == adress) {
                            mem_block = block;
                        }
                    }
                }
            }
        }
        return mem_block;
    }
    //_____________________________________________________________________________________________________________________________________________//
    public MemoryBlock getBlock(int length) {
        for(ArrayList<MemoryBlock> list : list_of_free_lists) {
            for(MemoryBlock blockfinder : list) {
                if(blockfinder.get_length().chunk_adress == length) {
                    return blockfinder;
                }
            }
        }
        return null;
    }
    //_____________________________________________________________________________________________________________________________________________//
    public ArrayList<MemoryBlock> returnMemoryList(int size) {
        for(ArrayList<MemoryBlock> list : list_of_memory_lists) {
            if(list.get(1).get_length().chunk_adress > size) {
                return list;
            }
        }
        return null;
    }
    //_____________________________________________________________________________________________________________________________________________//
    public MemoryBlock getFirstFreeBlock() {
        MemoryBlock free_block = null;
        for(ArrayList<MemoryBlock> list : list_of_free_lists) {
            for(MemoryBlock block : list) {
                if(free_block == null || block.get_length().chunk_adress < free_block.get_length().chunk_adress) {
                    free_block = block;
                }
            }
        }
        return free_block;
    }
    //_____________________________________________________________________________________________________________________________________________//

    public int mallocCall(MemoryBlock free_block, int actualsize, ArrayList<MemoryBlock> current_memory_list) {
        /*
         * mallocCall will iterate through free blocks for a suitable First Fit with pointers to next free blocks.
         * This method is used when allocating a memory chunk (either with first malloc or after sbrk is called)
         * Call to coalescing() here is necessary when mallocCall is called for an sbrk generated block to make instant coalescing
         */
        coalescing();                                                                                                    // Make sure all adjacent free blocks are merged
        while (free_block != null) {
            if(free_block.get_actual_chunk().chunk_size >= actualsize) {                                                 // First Fit structure, the first free block that can fit the malloc block is selected
                current_memory_list = returnMemoryList(free_block.get_length().chunk_adress);
                ArrayList<MemoryBlock> freeList = findList(free_block);
                int free_list_current_size = currentFreelistSize(freeList);
                
                // Define new memory chunks (metadata) for the allocated block starting in the free blocks first adress
                int current_adress = free_block.get_length().chunk_adress;

                // This is our new malloc memory block
                MemoryChunk previousLength = new MemoryChunk(current_adress, false, 32, null, null);
                current_adress += previousLength.chunk_size;
                // Data is what goes inside a memory block (metadata for what we actually store, like integers), the size of it is the size in bytes of the free space
                Data[] data = new Data[actualsize/8];
                MemoryChunk actual_chunk = new MemoryChunk(current_adress, false, actualsize, data, null);
                current_adress += actual_chunk.chunk_size;

                MemoryChunk length = new MemoryChunk(current_adress, false, 32, null, null);
                current_adress += length.chunk_size;
                // Create the malloc block with the chunks above
                MemoryBlock mallocBlock = new MemoryBlock(previousLength, length, actual_chunk, false);
                // Add the block to the memory list
                current_memory_list.add(mallocBlock);

                // Change the pointers of the free block where malloc allocated mallocBlock (making it smaller)
                free_block.get_length().chunk_adress = current_adress;
                current_adress += 32;
                free_block.get_previous().chunk_adress = current_adress;
                current_adress += 32;
                free_block.get_next().chunk_adress = current_adress;
                current_adress += 32;
                free_block.get_actual_chunk().chunk_adress = current_adress;
                free_block.get_actual_chunk().chunk_size -= actualsize + 64;
                // Pointer to the begginning of the allocated block (excluding header)
                System.out.println("memory allocated");
                System.out.println("Pointer: " + hexConverter(mallocBlock.get_actual_chunk().chunk_adress/8) + "\n");   // div by 8 to get the byte adress

                // If the free block reduces to a smaller size range, remove it from the current free list and add it to a smaller free list
                if(free_list_current_size != 32 && free_list_current_size/2 > free_block.get_actual_chunk().chunk_size/8) {
                    freeList.remove(free_block);
                    ArrayList<MemoryBlock> smallerList = bestFit(free_block.get_actual_chunk().chunk_size/8);
                    smallerList.add(free_block);
                }
                return mallocBlock.get_actual_chunk().chunk_adress;                                                     // Return the adress of where the newly allocated chunk beggins
            }
            
            if(free_block.get_next().pointer != null) {
                free_block = getBlock(free_block.get_next().pointer);
            } else {free_block = null;}
        }
        return -1;                                                                                                      // This line is reached if we don't find any free blocks with enough space -> call sbrk
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Method to store a string in a memory block
    public Integer storeString(int start_adress, String string) {

        Integer return_address = start_adress;

        System.err.print("Storing string '" + string  + "'... ");
        
        MemoryBlock block = getMemoryBlock(start_adress);
        if(block == null) {
            System.out.println("Error. Cannot allocate string. Memory not found.");
            return null;
        }
        Data[] block_data = block.get_actual_chunk().data;
        string += '\0';                                                                     // Simulates the null pointer terminator from C
        // Will find available bytes in the memory block to allocate each of the strings' characters
        for (int i = 0; i < string.length(); i++) {
            Integer temporal_adress = start_adress;
            char character = string.charAt(i);
            int character_int = (int) string.charAt(i);
            for(int j = 0; j < block_data.length; j++) {
                Data current_data = block_data[j];
                if(current_data == null) {
                    Data new_current_data = new Data(temporal_adress, character_int, null);
                    block_data[j] = new_current_data;

                    if(character == '\0') {
                        System.err.println("string stored.");
                        return return_address;
                    }
                    if(i == 0) {return_address = temporal_adress;}    
                    break;
                }
                temporal_adress += 8;                                                       // 1 byte increment per character
            }
        }
        System.err.println("storing process unfinished (no space left). Exiting.");
        if(return_address == start_adress) {
            return -1;
        } return return_address;
    }
    public String retreiveString(int adress) {
        // Retrieve string given an address of a pointer.
        String return_string = "";
        System.out.print("Retrieving string... ");

        MemoryBlock block = getBlockByData(adress);
        if(block == null) {
            System.out.println("could not retreive string (not found). Exiting.\n");
            return null;
        } else {System.out.println("string found:\n");}

        Data[] data = block.get_actual_chunk().data;
        for(int i = 0; i < data.length; i++) {
            Data current_data = data[i];
            if(current_data != null && current_data.adress == adress) {
                char letter = (char) current_data.actual_data;
                if(letter == '\0') {
                    System.out.println("Null terminator '\\0'  adress: " + hexConverter(current_data.adress/8) + "\n");
                    return return_string;
                }
                System.out.println("char '" + letter + "'  adress: " + hexConverter(current_data.adress/8));
                return_string += letter;
                adress += 8;
            }
        }
        return "null";
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Methods to store an int in a Memory Block
    public Integer storeInt(Integer pointer, Integer integer) {
        Integer return_adress = pointer;

        System.out.print("Storing int value '" + integer + "'... ");
        MemoryBlock block = getMemoryBlock(pointer);
        if(block == null) {
            System.out.println("could not find the space in memory. Exiting.");
            return -1;
        }
        String binary = String.format("%32s", Integer.toBinaryString(integer)).replace(' ', '0');

        Data[] data = block.get_actual_chunk().data;
        int start_index = 0;
        int end_index = 8;
        int temporal_adress = pointer;
        for(int i = 0; i < data.length; i++) {
            
            if(data[i] == null) { 
                String digits = binary.substring(start_index, end_index);
                Data data_value = new Data(temporal_adress, -1, digits);
                data[i] = data_value;
                if(end_index == 32) {
                    System.err.println("int value stored.");
                    return return_adress;
                }
                if(start_index == 0) {return_adress = temporal_adress;}
                start_index += 8;
                end_index += 8;
            }
            temporal_adress += 8;
        }
        if(return_adress == pointer) {
            System.out.println("Error storing int. Exiting.");
            return -1;
        } else {return return_adress;}
    }
    public Integer retrieveInt(int address) {
        System.out.print("Retrieving int value... ");
        String string_to_int = "";
        MemoryBlock block = getBlockByData(address);
        if(block == null) {
            System.out.println("could not retrieve int value (not found). Exiting.\n");
            return null;
        } else {System.out.println("int value found.\n");}

        Data[] data = block.get_actual_chunk().data;
        int counter = 0;
        for(int i = 0; i < data.length; i++) {
            Data current_data = data[i];
            if(current_data != null && current_data.adress == address) {
                String chunk_string = current_data.actual_data_string;
                string_to_int += chunk_string;
                if(counter == 3) {
                    int final_num = (int)Long.parseLong(string_to_int, 2);
                    return final_num;
                }
                counter++;
                address += 8;
            }
        }
        // Could not find the full value
        return null;
    }
    //_____________________________________________________________________________________________________________________________________________//
    // Class for data stored memory blocks (e.g., char).
    private class Data {
        Integer adress;
        int actual_data;
        String actual_data_string;
        public Data(int adress, int actual_data, String actual_data_string) {
            this.adress = adress;
            this.actual_data = actual_data;
            this.actual_data_string = actual_data_string;
        }
    }
}