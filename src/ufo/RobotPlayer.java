package ufo;

import battlecode.common.*;

public strictfp class RobotPlayer {

    enum State {
        NO_STATE,
        FIND_SOUP,
        FIND_HEADQUARTER,
        BUILD_REFINERY,
        BUILD_DESIGN_SCHOOL,
        MINE_SOUP,
    }

    enum Cell {
        NOT_DISCOVERED,
        NOTHING,
        SOUP,
        HQ,
        REFINERY,
        DESIGN_SCHOOL,
        FULFILLMENT_CENTER,
        NET_GUN;
        boolean team;
    }

    static Direction[] directions = {
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.SOUTHEAST,
            Direction.WEST,
            null,
            Direction.EAST,
            Direction.NORTHWEST,
            Direction.NORTH,
            Direction.NORTHEAST,
    };

    static RobotController rc;

    State state = State.NO_STATE;

    static int turnCount;

    Cell[][] saveMap = new Cell[64][64];

    //Helpers:

    void setInSaveMap(MapLocation mapLocation, Cell cell) {
        saveMap[mapLocation.x][mapLocation.y] = cell;
    }

    Cell getFromSaveMap(MapLocation mapLocation) {
        return saveMap[mapLocation.x][mapLocation.y];
    }

    static int distance(MapLocation a, MapLocation b) {
        return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y);
    }

    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        turnCount = 0;

        while (true) {
            turnCount += 1;
            try {
                switch (rc.getType()) {
                    case HQ:
                        runHQ();
                        break;
                    case MINER:
                        runMiner();
                        break;
                    case REFINERY:
                        runRefinery();
                        break;
                    case VAPORATOR:
                        runVaporator();
                        break;
                    case DESIGN_SCHOOL:
                        runDesignSchool();
                        break;
                    case FULFILLMENT_CENTER:
                        runFulfillmentCenter();
                        break;
                    case LANDSCAPER:
                        runLandscaper();
                        break;
                    case DELIVERY_DRONE:
                        runDeliveryDrone();
                        break;
                    case NET_GUN:
                        runNetGun();
                        break;
                }

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    static void runHQ() throws GameActionException {
    }

    static void runMiner() throws GameActionException {
    }

    static void runRefinery() throws GameActionException {
    }

    static void runVaporator() throws GameActionException {
    }

    static void runDesignSchool() throws GameActionException {
    }

    static void runFulfillmentCenter() throws GameActionException {
    }

    static void runLandscaper() throws GameActionException {
    }

    static void runDeliveryDrone() throws GameActionException {
    }

    static void runNetGun() throws GameActionException {
    }
}
