package ufo;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

public strictfp class RobotPlayer {
    static Random rand;

    enum State {
        ATTACK,
        NO_STATE,
        MINER_EXPLORE,
        MINER_RETURN,
        BUILD,
        DESIGNSCHOOL_DEFEND,
        LANDSCAPER_DEFEND,
        UNKNOWN,
        MINER_SOUP,
        DROP_FRIEND,
        DRONE_EXPLORE,
        DROP_ENEMY,
        DROP_DEFENDER
    }

    enum DroneType {
        ATTACK,
        NORMAL
    }

    static class Cell {
        boolean flooded, visited;
        RobotType robot;
        int elevation, soup;
        int last_ignore = -1000;
    }

    static Direction[] directions = {
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.SOUTHEAST,
            Direction.WEST,
            Direction.CENTER,
            Direction.EAST,
            Direction.NORTHWEST,
            Direction.NORTH,
            Direction.NORTHEAST,
    };

    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};


    static RobotController rc;

    static State state = State.NO_STATE;

    static int turnCount;

    //    static Cell[][] cells;
    static int[][] last_ignore;
    static final int shift = 10;
    static MapLocation[] neighbours = new MapLocation[200];
    static int sizeNeighbours;
    static ArrayList<MapLocation> history = new ArrayList<>();
    static ArrayList<MapLocation> preferred = new ArrayList<>();

    //Helpers:

    static int getLastIgnore(MapLocation mapLocation) {
        return last_ignore[mapLocation.x + shift][mapLocation.y + shift];
    }

    static void setLastIgnore(MapLocation mapLocation, int val) {
        last_ignore[mapLocation.x + shift][mapLocation.y + shift] = val;
    }

/*
    static void setCell(MapLocation mapLocation, Cell cell) {
        cells[mapLocation.x][mapLocation.y] = cell;
    }

    static Cell getCell(MapLocation mapLocation) {
        return cells[mapLocation.x + shift][mapLocation.y + shift];
    }
*/

    static int distance(MapLocation a, MapLocation b) {
        return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y);
    }

    private static void getNeighbours() {
        int r = (int) Math.sqrt(rc.getType().sensorRadiusSquared) + 2;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                if (dx * dx + dy * dy <= rc.getType().sensorRadiusSquared)
                    neighbours[sizeNeighbours++] = new MapLocation(dx, dy);
    }

    public static void run(RobotController rc) throws GameActionException {
        rand = new Random(rc.getID());
        RobotPlayer.rc = rc;
        turnCount = 0;
        state = State.UNKNOWN;

      /*  cells = new Cell[rc.getMapWidth() + shift * 2][rc.getMapHeight() + shift * 2];
        for (int i = shift; i < rc.getMapWidth() + shift; i++)
            for (int j = shift; j < rc.getMapHeight() + shift; j++) {
                cells[i][j] = new Cell();
            }*/

        last_ignore = new int[rc.getMapWidth() + shift * 2][rc.getMapHeight() + shift * 2];

        try {
            getNeighbours();
        } catch (Exception e) {
            System.out.println(rc.getType() + " Exception");
            e.printStackTrace();
        }

        while (true) {
            turnCount += 1;
            try {
                senseNeighbours();
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

    static RobotInfo[] robots;
    static MapLocation[] soups;

    static void senseNeighbours() throws GameActionException {
        robots = rc.senseNearbyRobots();
        soups = rc.senseNearbySoup();

        /*for (int i = 0; i < sizeNeighbours; i++) {
            MapLocation d = neighbours[i];
            MapLocation loc = rc.getLocation().translate(d.x, d.y);
            if (rc.canSenseLocation(loc)) {
                Cell cell = getCell(loc);
                cell.visited = true;
                cell.elevation = rc.senseElevation(loc);
                cell.flooded = rc.senseFlooding(loc);
                cell.soup = rc.senseSoup(loc);
            }
        }

        for (int i = 0; i < robots.length; i++) {
            RobotInfo info = robots[i];
            getCell(info.getLocation()).robot = info.getType();
        }*/
    }

    static int numberOfMiners = 0, lastMinerCreatedTurn = 0;
    static final int soupSaveTurns = 4;
    static int lastTurnSoup[] = new int[soupSaveTurns];


    static void runHQ() throws GameActionException {
        int hereRobots = 0;
        for (RobotInfo robot : robots) {
            if (robot.getTeam() != rc.getTeam() && robot.getType() == RobotType.DELIVERY_DRONE)
                if (rc.canShootUnit(robot.getID()))
                    rc.shootUnit(robot.getID());
            if (robot.getTeam() == rc.getTeam() && !robot.getType().isBuilding() && hamiltonianDistance(rc.getLocation(), robot.getLocation()) < defenceRadius)
                hereRobots++;
        }
        int differenceSoup = rc.getTeamSoup() - lastTurnSoup[0];
        if (hereRobots < 3 || (turnCount - lastMinerCreatedTurn > 200))
            if (numberOfMiners < 7 || differenceSoup > RobotType.MINER.cost * 2.5 ||
                    (rc.getTeamSoup() > 1000 && turnCount - lastMinerCreatedTurn > 50) || (turnCount - lastMinerCreatedTurn > 150)) {
                int x = rand.nextInt(directions.length);
                for (int i = x; i < directions.length + x; i++) {
                    Direction direction = directions[i % directions.length];
                    if (hamiltonianDistance(rc.adjacentLocation(direction), rc.getLocation()) == 1 && rc.canBuildRobot(RobotType.MINER, direction)) {
                        rc.buildRobot(RobotType.MINER, direction);
                        lastMinerCreatedTurn = turnCount;
                        numberOfMiners++;
                    }
                }
            }
        for (int i = 0; i < soupSaveTurns - 1; i++)
            lastTurnSoup[i] = lastTurnSoup[i + 1];
        lastTurnSoup[soupSaveTurns - 1] = rc.getTeamSoup();
    }

    static boolean backToHome = false;

    static MapLocation HQ_Save = null;

    private static int lastSoupAction;

    static void runMiner() throws GameActionException {
        if (state == State.UNKNOWN || (HQ_Save != null && hamiltonianDistance(rc.getLocation(), HQ_Save) == 1 && rc.getSoupCarrying() == 0)) {
            for (RobotInfo robot : robots)
                if (robot.getType() == RobotType.HQ && robot.getTeam() == rc.getTeam())
                    HQ_Save = robot.getLocation();
            if (rc.getTeamSoup() > 400) {
                for (Direction direction : directions) {
                    MapLocation loc = rc.adjacentLocation(direction);
                    if (rc.canSenseLocation(loc)) {
                        RobotInfo robotInfo = rc.senseRobotAtLocation(loc);
                        if (robotInfo != null && robotInfo.team == rc.getTeam() && robotInfo.type == RobotType.HQ) {
                            state = State.BUILD;
                        }
                    }

                }
            }
            if (state != State.BUILD) {
                state = State.MINER_EXPLORE;
                minerInitExplore();
            }
        }
        if (state == State.BUILD) {
            boolean isThereDesignSchool = false, isThereFulfillmentCenter = false, isThereFirstVaporator = false, isThereSecondVaporator = false;
            for (RobotInfo robot : robots)
                if (robot.team == rc.getTeam()) {
                    if (robot.type == RobotType.DESIGN_SCHOOL)
                        isThereDesignSchool = true;
                    if (robot.type == RobotType.FULFILLMENT_CENTER)
                        isThereFulfillmentCenter = true;
                    if (robot.type == RobotType.VAPORATOR)
                        if (isThereFirstVaporator)
                            isThereSecondVaporator = true;
                        else
                            isThereFirstVaporator = true;
                }
            if (isThereDesignSchool && isThereFulfillmentCenter && isThereFirstVaporator && isThereSecondVaporator) {
                state = State.MINER_EXPLORE;
                minerInitExplore();
            }
            if (state == State.BUILD) {
                boolean empty = false;
                for (Direction direction : directions) {
                    MapLocation loc = rc.adjacentLocation(direction);
                    if (hamiltonianDistance(loc, HQ_Save) == 2 && loc.isAdjacentTo(HQ_Save)) {
                        if (!isThereDesignSchool && rc.canBuildRobot(RobotType.DESIGN_SCHOOL, direction)) {
                            rc.buildRobot(RobotType.DESIGN_SCHOOL, direction);
                        } else if (!isThereFulfillmentCenter && rc.canBuildRobot(RobotType.FULFILLMENT_CENTER, direction)) {
                            rc.buildRobot(RobotType.FULFILLMENT_CENTER, direction);
                        } else if (!isThereFirstVaporator && rc.canBuildRobot(RobotType.VAPORATOR, direction)) {
                            rc.buildRobot(RobotType.VAPORATOR, direction);
                        } else if (!isThereSecondVaporator && rc.canBuildRobot(RobotType.VAPORATOR, direction)) {
                            rc.buildRobot(RobotType.VAPORATOR, direction);
                        }
                        if (rc.canSenseLocation(loc) && rc.senseRobotAtLocation(loc) == null)
                            empty = true;
                    }
                }
                if (!empty) {
                    state = State.MINER_EXPLORE;
                    minerInitExplore();
                }
            }
        }


        if (state == State.MINER_EXPLORE && rc.isReady()) {
            MapLocation soup = getASoup();
            if (soup != null) {
                destination = soup;
                if (rc.canMineSoup(rc.getLocation().directionTo(soup))) {
                    okToUsePreferred = false;
                    preferred.clear();
                    state = State.MINER_SOUP;
                    backToHome = false;
                }
            }
            if (rc.getSoupCarrying() > 0 && soup == null || rc.getSoupCarrying() == RobotType.MINER.soupLimit) {
                for (Direction dir : directions)
                    if (rc.canDepositSoup(dir)
                            && rc.senseRobotAtLocation(rc.getLocation().add(dir)).getTeam() == rc.getTeam()) {
                        rc.depositSoup(dir, rc.getSoupCarrying());
                        lastSoupAction = turnCount;
                        okToUsePreferred = true;
                        backToHome = false;
                        history.clear();
                        history.add(rc.getLocation());
                        return;
                    }

                MapLocation refinery = null;
                for (RobotInfo robot : robots)
                    if (robot.type == RobotType.REFINERY && robot.team == rc.getTeam())
                        refinery = robot.getLocation();

                int soupSum = 0;
                for (MapLocation soupTmp : soups)
                    soupSum += rc.senseSoup(soupTmp);

                if (rc.getTeamSoup() > RobotType.REFINERY.cost &&
                        ((lastSoupAction < turnCount - 50 && rc.getTeamSoup() > 350)
                                || soupSum > 400 && refinery == null && getNearbyHQ() == null)) {
                    for (Direction direction : directions)
                        if (rc.canBuildRobot(RobotType.REFINERY, direction)) {
                            rc.buildRobot(RobotType.REFINERY, direction);
                            return;
                        }
                }

                if (refinery == null) {
                    backToHome = true;
                    MapLocation pathToHQ = history.get(history.size() - 1);
                    if (getLastIgnore(pathToHQ) > turnCount) {
                        if (history.size() != 1) {
                            for (int i = 0; i < 5 && history.size() >= 2; i++)
                                history.remove(history.size() - 1);
                            pathToHQ = history.get(history.size() - 1);
                            preferred.add(pathToHQ);
                        } else pathToHQ = destination;
                    }
                    destination = pathToHQ;
                } else destination = refinery;
            } else {
                if (rc.getTeamSoup() > 400) {
                    int netguns = 0, drones = 0;
                    for (RobotInfo robot : robots)
                        if (robot.getType() == RobotType.DELIVERY_DRONE && robot.getTeam() != rc.getTeam())
                            drones++;
                        else if (robot.getType() == RobotType.NET_GUN && robot.getTeam() == rc.getTeam())
                            netguns++;
                    if (drones > 0 && netguns == 0) {
                        for (Direction direction : directions)
                            if (rc.canBuildRobot(RobotType.NET_GUN, direction)) {
                                rc.buildRobot(RobotType.NET_GUN, direction);
                                return;
                            }
                    }
                    if (rc.getTeamSoup() > 600)
                        for (Direction direction : directions) {
                            MapLocation nextLoc = rc.adjacentLocation(direction);
                            if (rc.canSenseLocation(nextLoc) && rc.senseElevation(nextLoc) > 50 && nextLoc.distanceSquaredTo(HQ_Save) > 100 && rc.canBuildRobot(RobotType.VAPORATOR, direction))
                                rc.buildRobot(RobotType.VAPORATOR, direction);
                        }
                }
            }
            if (state == State.MINER_EXPLORE && rc.isReady()) {
                Direction dir = moveTowards(destination);
                if (dir == Direction.CENTER) {
                    setLastIgnore(destination, turnCount + (int) Math.sqrt(rc.getType().sensorRadiusSquared * (rc.getCooldownTurns() + 1)) + 4);
                    minerInitExplore();
                } else {
                    if (!backToHome)
                        history.add(rc.getLocation());
                    rc.move(dir);
                }
            }
        }

        if (state == State.MINER_SOUP && rc.isReady()) {
            Direction dir = rc.getLocation().directionTo(destination);
            if (rc.canMineSoup(dir)) {
                rc.mineSoup(dir);
                lastSoupAction = turnCount;
            } else
                state = State.MINER_EXPLORE;
        }
    }

    private static Direction landscaperMoveTowards(MapLocation dest) throws GameActionException {
        Direction bestDir = Direction.CENTER;

        for (Direction dir : directions) {
            MapLocation nextLoc = rc.adjacentLocation(dir);
            RobotInfo robot = (rc.canSenseLocation(nextLoc) ? rc.senseRobotAtLocation(nextLoc) : null);
            if (nextLoc.distanceSquaredTo(dest)
                    < rc.adjacentLocation(bestDir).distanceSquaredTo(dest)
                    && hamiltonianDistance(defenceOrigin, nextLoc) <= defenceRadius
                    && robot == null) {
                bestDir = dir;
            }
        }

        return bestDir;
    }

    private static Direction droneMoveTowards(MapLocation dest) throws GameActionException {
        Direction bestDir = Direction.CENTER;
        ArrayList<MapLocation> netguns = new ArrayList<>();
        for (RobotInfo robot : robots)
            if (robot.getType() == RobotType.NET_GUN && robot.getTeam() != rc.getTeam())
                netguns.add(robot.getLocation());
        for (Direction dir : directions) {
            MapLocation nextLoc = rc.adjacentLocation(dir);
            boolean nearNetgun = false, onNetGun = false;
            for (MapLocation netgun : netguns) {
                if (nextLoc.distanceSquaredTo(netgun) < GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED)
                    nearNetgun = true;
                if (rc.getLocation().distanceSquaredTo(netgun) < GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED)
                    onNetGun = true;
            }
            if (onNetGun)
                nearNetgun = false;
            if (!nearNetgun && nextLoc.distanceSquaredTo(dest)
                    < rc.adjacentLocation(bestDir).distanceSquaredTo(dest)
                    && rc.canMove(dir) && (hamiltonianDistance(rc.getLocation(), homeDeliveryDrone) <= defenceRadius || goingToEnemy || hamiltonianDistance(nextLoc, homeDeliveryDrone) > defenceRadius)) {
                bestDir = dir;

            }
        }

        return bestDir;
    }

    private static Direction moveTowards(MapLocation dest) throws GameActionException {
        Direction bestDir = Direction.CENTER;

        for (Direction dir : directions) {
            MapLocation nextLoc = rc.adjacentLocation(dir);
            if (nextLoc.distanceSquaredTo(dest)
                    < rc.adjacentLocation(bestDir).distanceSquaredTo(dest)
                    && rc.canMove(dir)
                    && rc.canSenseLocation(nextLoc)
                    && !rc.senseFlooding(nextLoc)) {
                bestDir = dir;
            }
        }

        return bestDir;
    }

    private static MapLocation getASoup() {
        MapLocation ret = null;
        for (int i = 0; i < soups.length; i++) {
            MapLocation loc = soups[i];
            if ((getLastIgnore(loc) < turnCount || getLastIgnore(loc) == 0) && (ret == null
                    || ret.distanceSquaredTo(rc.getLocation()) > loc.distanceSquaredTo(rc.getLocation())))
                ret = loc;
        }

        return ret;
    }

    static MapLocation destination;
    static boolean okToUsePreferred = false;

    private static void minerInitExplore() {
        if (okToUsePreferred && preferred.size() > 0) {
            destination = preferred.get(preferred.size() - 1);
            preferred.remove(preferred.size() - 1);
        } else randomDestination();
    }

    static void runRefinery() throws GameActionException {
    }

    static void runVaporator() throws GameActionException {
    }


    static MapLocation getNearbyHQ() throws GameActionException {
        for (int i = 0; i < sizeNeighbours; i++) {
            MapLocation d = neighbours[i];
            MapLocation loc = rc.getLocation().translate(d.x, d.y);
            if (rc.canSenseLocation(loc)) {
                RobotInfo robot = null;
                try {
                    robot = rc.senseRobotAtLocation(loc);
                } catch (GameActionException e) {
                    e.printStackTrace();
                }
                if (robot != null && robot.type == RobotType.HQ && robot.team == rc.getTeam())
                    return loc;
            }
        }
        return null;
    }

    static int defenceRadius = 3;
    static int defendingRobotsNumber, extraLandscapers;
    static MapLocation defenceOrigin, defencePosition;
    static MapLocation[] defencePositions = new MapLocation[4 * defenceRadius];
    static Direction defenceDirection;
    static boolean positionFull[] = new boolean[4 * defenceRadius];

    static int landScapersBuilt = 0, lastLandscaperBuilt = 0;

    static void runDesignSchool() throws GameActionException {
        int differenceSoup = rc.getTeamSoup() - lastTurnSoup[0];
        for (int i = 0; i < soupSaveTurns - 1; i++)
            lastTurnSoup[i] = lastTurnSoup[i + 1];
        lastTurnSoup[soupSaveTurns - 1] = rc.getTeamSoup();

        if (state == State.UNKNOWN) {
            defenceOrigin = getNearbyHQ();
            if (defenceOrigin != null) {
                state = State.DESIGNSCHOOL_DEFEND;

                extraLandscapers = 1;
                defendingRobotsNumber = 0;
                for (int dx = -defenceRadius; dx <= defenceRadius; dx++) {
                    int dy = defenceRadius - Math.abs(dx);
                    if (isOnMap(defenceOrigin.translate(dx, dy)))
                        defencePositions[defendingRobotsNumber++] = defenceOrigin.translate(dx, dy);

                    if (isOnMap(defenceOrigin.translate(dx, dy + 1)))
                        extraLandscapers++;

                    dy = -dy;

                    if (dy != 0) {
                        if (isOnMap(defenceOrigin.translate(dx, dy)))
                            defencePositions[defendingRobotsNumber++] = defenceOrigin.translate(dx, dy);
                    }

                    if (isOnMap(defenceOrigin.translate(dx, dy - 1)))
                        extraLandscapers++;
                }
            }
        }

        if (state == State.DESIGNSCHOOL_DEFEND) {
            int cnt = countNearbyDefense();
            int hereRobots = 0;
            for (RobotInfo robot : robots)
                if (robot.getType() == RobotType.HQ && robot.getTeam() == rc.getTeam())
                    HQ_Save = robot.getLocation();
            for (RobotInfo robot : robots)
                if (robot.getTeam() == rc.getTeam() && !robot.getType().isBuilding() && hamiltonianDistance(HQ_Save, robot.getLocation()) < defenceRadius)
                    hereRobots++;
            if (hereRobots < 3)
                if (cnt < defendingRobotsNumber || differenceSoup > RobotType.LANDSCAPER.cost * 0.4
                        || (turnCount - lastLandscaperBuilt > (turnCount > 1000 ? 300 : 150)))
                    for (Direction dir : directions)
                        if (rc.canBuildRobot(RobotType.LANDSCAPER, dir)) {
                            rc.buildRobot(RobotType.LANDSCAPER, dir);
                            lastLandscaperBuilt = turnCount;
                            landScapersBuilt++;
                            break;
                        }
        }
    }

    static boolean isOnMap(MapLocation loc) {
        return loc.x >= 0 && loc.y >= 0 && loc.x < rc.getMapWidth() && loc.y < rc.getMapHeight();
    }

    static void runFulfillmentCenter() throws GameActionException {
        int differenceSoup = rc.getTeamSoup() - lastTurnSoup[0];
        int hereDeliveryDrones = 0;
        for (RobotInfo robot : robots)
            if (robot.getType() == RobotType.HQ && robot.getTeam() == rc.getTeam())
                HQ_Save = robot.getLocation();
        for (RobotInfo robot : robots)
            if (robot.getTeam() == rc.getTeam() && robot.getType() == RobotType.DELIVERY_DRONE && hamiltonianDistance(HQ_Save, robot.getLocation()) < defenceRadius)
                hereDeliveryDrones++;
        if (hereDeliveryDrones < 3)
            if (differenceSoup > RobotType.DELIVERY_DRONE.cost / 1.5 || rc.getTeamSoup() > 600) {
                int x = rand.nextInt(directions.length);
                for (int i = x; i < x + directions.length; i++) {
                    Direction direction = directions[i % directions.length];
                    if (rc.canBuildRobot(RobotType.DELIVERY_DRONE, direction))
                        rc.buildRobot(RobotType.DELIVERY_DRONE, direction);
                }
            }
        for (int i = 0; i < soupSaveTurns - 1; i++)
            lastTurnSoup[i] = lastTurnSoup[i + 1];
        lastTurnSoup[soupSaveTurns - 1] = rc.getTeamSoup();
    }

    static int countNearbyDefense() {
        int cnt = 0;
        for (RobotInfo robot : robots)
            if (robot.type == RobotType.LANDSCAPER && robot.team == rc.getTeam())
                cnt++;
        return cnt;
    }

    static int hamiltonianDistance(MapLocation a, MapLocation b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    static int lastMoved = 0, depositCount = 0;
    static final int fixWallTurn = 400, innerWallTurn = 200;
  /*  static boolean[][] floodFlag;

    static boolean getFloodFlag(MapLocation mapLocation){return floodFlag[mapLocation.x][mapLocation.y];}
    static void setFloodFlag(MapLocation mapLocation, boolean val){floodFlag[mapLocation.x][mapLocation.y] = val;}
*/

    static int lastPositionChange = 0;

    static void runLandscaper() throws GameActionException {
        if (state == State.UNKNOWN) {
//            floodFlag = new boolean[rc.getMapWidth() + 2*shift][rc.getMapHeight() + 2 * shift];
            if (defenceOrigin == null)
                defenceOrigin = getNearbyHQ();
            defendingRobotsNumber = 0;
            if (defenceOrigin != null)
                for (int dx = -defenceRadius; dx <= defenceRadius; dx++) {
                    int dy = defenceRadius - Math.abs(dx);
                    if (isOnMap(defenceOrigin.translate(dx, dy)))
                        defencePositions[defendingRobotsNumber++] = defenceOrigin.translate(dx, dy);

                    if (dy != 0) {
                        dy = -dy;
                        if (isOnMap(defenceOrigin.translate(dx, dy)))
                            defencePositions[defendingRobotsNumber++] = defenceOrigin.translate(dx, dy);
                    }
                }
            if (defenceOrigin != null && countNearbyDefense() <= defendingRobotsNumber)
                state = State.LANDSCAPER_DEFEND;
            else
                state = State.ATTACK;
        }
        if (defenceOrigin == null)
            defenceOrigin = getNearbyHQ();
        if (defenceOrigin == null || rc.getLocation().distanceSquaredTo(defenceOrigin) > 100)
            state = State.ATTACK;
        else if (Math.abs(hamiltonianDistance(defenceOrigin, rc.getLocation()) - defenceRadius) <= 1 && state == State.ATTACK) {
            state = State.UNKNOWN;
            return;
        }
        if (state == State.ATTACK && rc.isReady()) {
            randomDestination();
            MapLocation tmpDest = null;
            for (RobotInfo robot : robots)
                if (robot.getType().isBuilding() && robot.getTeam() != rc.getTeam())
                    if (tmpDest == null || (rc.getLocation().distanceSquaredTo(tmpDest) > rc.getLocation().distanceSquaredTo(robot.getLocation())))
                        tmpDest = robot.getLocation();
            destination = tmpDest;
            if (destination != null && destination.isAdjacentTo(rc.getLocation())) {
                if (rc.getDirtCarrying() > 0) {
                    if (rc.canDepositDirt(rc.getLocation().directionTo(destination)))
                        rc.depositDirt(rc.getLocation().directionTo(destination));
                } else {
                    for (Direction direction : directions)
                        if (direction != Direction.CENTER && rc.canDigDirt(direction))
                            rc.digDirt(direction);
                }
            }
            if (destination == null)
                randomDestination();
            Direction dir = moveTowards(destination);
            if (dir == Direction.CENTER)
                randomDestination();
            else if (rc.canMove(dir))
                rc.move(dir);
        }


        if (state == State.LANDSCAPER_DEFEND) {
            if (rc.isReady()) {
                MapLocation tmp = defencePosition;
                if (!rc.getLocation().equals(defencePosition) && (defencePosition == null
                        || locationContainsDefendingLandscaper(defencePosition) || lastMoved < turnCount - 10)) {
                    defencePosition = getDefencePosition();
                    if (defencePosition == null) {
                        return;
                    }
                    lastMoved = turnCount;
                }

                if (lastPositionChange < turnCount - 20)
                    setLastIgnore(defencePosition, turnCount);

                if (!defencePosition.equals(tmp)) lastPositionChange = turnCount;

                // Don't destroy the wall if it's too late
                if (!rc.getLocation().equals(defencePosition) && turnCount > fixWallTurn)
                    return;


                if (rc.getLocation().equals(defencePosition)) {
                    defenceDirection = defenceOrigin.directionTo(defencePosition);

                    if (rc.getDirtCarrying() > 0 && rc.isReady()) {
                        int dx = 0, dy = 0;
                        if (defenceDirection.dx == 0) {
                            dy = 0;
                            if (depositCount % 4 == 0)
                                dx = 1;
                            if (depositCount % 4 == 1)
                                dx = -1;
                        } else if (defenceDirection.dy == 0) {
                            dx = 0;
                            if (depositCount % 4 == 0)
                                dy = 1;
                            if (depositCount % 4 == 1)
                                dy = -1;
                        } else {
                            switch (depositCount % 4) {
                                case 1:
                                    dx = defenceDirection.dx;
                                    break;
                                case 2:
                                    dy = defenceDirection.dy;
                                    break;
                            }
                        }
                        Direction depDir = (new MapLocation(0, 0)).directionTo(new MapLocation(dx, dy));

                        try {
                            depDir = Direction.CENTER;
                            int minElev = rc.senseElevation(rc.getLocation().add(depDir));
                            for (Direction dir : directions) {
                                MapLocation loc = rc.getLocation().add(dir);
                                int distance = hamiltonianDistance(loc, defenceOrigin),
                                        elev = (rc.senseFlooding(loc) ? -1000 : rc.senseElevation(loc));

                                if (((distance == defenceRadius
                                        && (locationContainsDefendingLandscaper(loc) || turnCount > fixWallTurn))
                                        || (turnCount > fixWallTurn / 4 && distance == defenceRadius + 1 && loc.x != defenceOrigin.x && loc.y != defenceOrigin.y)
                                        || (distance <= defenceRadius && rc.senseFlooding(loc))
                                        /*|| getFloodFlag(loc)*/)
                                        && (elev < minElev)) {
                                    minElev = elev;
                                    depDir = dir;
                                    /*if (rc.senseFlooding(loc)) setFloodFlag(loc, true);*/
                                }
                            }
                        } catch (Exception e) {
                            System.out.println(rc.getType() + " Exception");
                            e.printStackTrace();
                        }

                        if (rc.canDepositDirt(depDir)) {
                            rc.depositDirt(depDir);
                            depositCount++;
                        }

                        return;
                    }


                    if (rc.canDigDirt(defenceDirection)) {
                        rc.digDirt(defenceDirection);
                    }
                } else {
                    Direction dir = landscaperMoveTowards(defencePosition);
                    if (dir == Direction.CENTER) return;

                    if (rc.canMove(dir)) {
                        rc.move(dir);
                        lastMoved = turnCount;
                    } else {
                        MapLocation nextLoc = rc.getLocation().add(dir);
                        if (rc.isReady() && rc.canSenseLocation(nextLoc)
                                && rc.senseRobotAtLocation(nextLoc) == null) {
                            if (rc.senseElevation(rc.getLocation()) > rc.senseElevation(nextLoc)) {
                                if (rc.getDirtCarrying() > 0) {
                                    if (rc.canDepositDirt(dir))
                                        rc.depositDirt(dir);
                                } else {
                                    if (rc.canDigDirt(Direction.CENTER))
                                        rc.digDirt(Direction.CENTER);
                                }
                            } else {
                                if (rc.getDirtCarrying() > 0) {
                                    if (rc.canDepositDirt(Direction.CENTER))
                                        rc.depositDirt(Direction.CENTER);
                                } else {
                                    if (rc.canDigDirt(dir))
                                        rc.digDirt(dir);
                                }
                            }
                        }


                    }
                }


            }
        }
    }

    static MapLocation getDefencePosition() throws GameActionException {
        if (hamiltonianDistance(rc.getLocation(), defenceOrigin) == defenceRadius
                || hamiltonianDistance(rc.getLocation(), defenceOrigin) == defenceRadius + 1)
            return rc.getLocation();

        MapLocation ret = null;
        for (int i = 0; i < defendingRobotsNumber; i++) {
            MapLocation loc = defencePositions[i];
            if (locationContainsDefendingLandscaper(loc))
                positionFull[i] = true;

            if (!positionFull[i] /*&& (getLastIgnore(loc) < turnCount - 30 || turnCount < 30)*/ && (ret == null
                    || rc.getLocation().distanceSquaredTo(loc) < rc.getLocation().distanceSquaredTo(ret)))
                ret = loc;
        }

        return ret;
    }

    static boolean locationContainsDefendingLandscaper(MapLocation loc) throws GameActionException {
        if (!rc.canSenseLocation(loc)) return false;
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        return (robot != null && robot.type == RobotType.LANDSCAPER && robot.team == rc.getTeam());
    }

    static MapLocation baseDeliveryDrone = null;
    static MapLocation homeDeliveryDrone = null;
    static MapLocation enemyHQ = null;
    static boolean goingToEnemy = false;
    static int homeElevation;
    static MapLocation[] defencePositionsDrone = new MapLocation[10 * defenceRadius];
    static int ignoreMiner = 0;
    static boolean firstMiner = false;
    static DroneType droneType = DroneType.NORMAL;
    static State pickUpUnit = State.DROP_FRIEND;

    static void runDeliveryDrone() throws GameActionException {
        if (turnCount == 1) {
            baseDeliveryDrone = rc.getLocation();
            //homeDeliveryDrone = rc.getLocation();
            for (RobotInfo robot : robots)
                if (robot.getType() == RobotType.HQ && robot.getTeam() == rc.getTeam()) {
                    homeDeliveryDrone = robot.getLocation();
                    homeElevation = rc.senseElevation(homeDeliveryDrone);
                    break;
                }

            defenceOrigin = homeDeliveryDrone;
            defendingRobotsNumber = 0;
            for (int dx = -defenceRadius; dx <= defenceRadius; dx++) {
                int dy = defenceRadius - Math.abs(dx);
                if (isOnMap(defenceOrigin.translate(dx, dy)))
                    defencePositionsDrone[defendingRobotsNumber++] = defenceOrigin.translate(dx, dy);
                if (dy != 0) {
                    dy = -dy;
                    if (isOnMap(defenceOrigin.translate(dx, dy)))
                        defencePositionsDrone[defendingRobotsNumber++] = defenceOrigin.translate(dx, dy);
                }
            }
            for (int dx = -defenceRadius - 1; dx <= defenceRadius + 1; dx++) {
                int dy = defenceRadius + 1 - Math.abs(dx);
                if (isOnMap(defenceOrigin.translate(dx, dy)) && dx != 0 && dy != 0 && Math.abs(dx) != 1)
                    defencePositionsDrone[defendingRobotsNumber++] = defenceOrigin.translate(dx, dy);
                if (dy != 0) {
                    dy = -dy;
                    if (isOnMap(defenceOrigin.translate(dx, dy)) && dx != 0 && dy != 0 && Math.abs(dx) != 1)
                        defencePositionsDrone[defendingRobotsNumber++] = defenceOrigin.translate(dx, dy);
                }
            }
            int deliveryDrones = 0;
            for (RobotInfo robot : robots)
                if (robot.getType() == RobotType.DELIVERY_DRONE && robot.getTeam() == rc.getTeam())
                    deliveryDrones++;
            if (rand.nextInt(3) == 0 && deliveryDrones > 3)
                droneType = DroneType.ATTACK;
        }
        if (rc.getLocation().distanceSquaredTo(homeDeliveryDrone) > 250)
            droneType = DroneType.ATTACK;
        if (droneType == DroneType.ATTACK) {
            findEnemyHQ();
            for (Direction direction : directions) {
                MapLocation loc = rc.adjacentLocation(direction);
                if (!rc.isCurrentlyHoldingUnit()) {
                    if (rc.canSenseLocation(loc)) {
                        RobotInfo robot = rc.senseRobotAtLocation(loc);
                        if (robot != null && robot.getTeam() != rc.getTeam())
                            if (rc.canPickUpUnit(robot.getID())) {
                                pickUpUnit = State.DROP_ENEMY;
                                rc.pickUpUnit(robot.getID());
                            }
                    }
                } else if (pickUpUnit == State.DROP_ENEMY) {
                    if (rc.canSenseLocation(loc) && rc.senseFlooding(loc) && rc.canDropUnit(direction))
                        rc.dropUnit(direction);
                } else if (pickUpUnit == State.DROP_FRIEND) {
                    if (rc.canSenseLocation(loc) && !rc.senseFlooding(loc) && rc.canDropUnit(direction))
                        rc.dropUnit(direction);
                }
            }
            if (destination == null)
                randomDestination();
            if (enemyHQ != null)
                destination = enemyHQ;
            Direction dir = droneMoveTowards(destination);
            if (dir == Direction.CENTER)
                randomDestination();
            dir = droneMoveTowards(destination);
            if (rc.canMove(dir))
                rc.move(dir);
            return;
        }
        if (turnCount > ignoreMiner + 40)
            firstMiner = false;
        goingToEnemy = false;
        if (state != State.DROP_FRIEND && state != State.DROP_ENEMY && state != State.DROP_DEFENDER) {
            findEnemyHQ();
            if (rc.getRoundNum() % 500 == 0 && enemyHQ != null)
                if (rand.nextInt(2) == 0) {
                    droneType = DroneType.ATTACK;
                    return;
                }
            MapLocation tmpLocation = null;
            if (rc.getLocation().distanceSquaredTo(homeDeliveryDrone) < 100)
                for (RobotInfo enemy : robots) {
                    if (!enemy.getType().isBuilding())
                        if (enemy.getTeam() != rc.getTeam() || (enemy.getType() == RobotType.MINER && hamiltonianDistance(enemy.getLocation(), homeDeliveryDrone) <= defenceRadius + 1) ||
                                (enemy.getType() == RobotType.LANDSCAPER && hamiltonianDistance(enemy.getLocation(), homeDeliveryDrone) < defenceRadius)) {
                            if (tmpLocation == null || rc.getLocation().distanceSquaredTo(tmpLocation) > rc.getLocation().distanceSquaredTo(enemy.getLocation()))
                                if (enemy.getType() == RobotType.MINER && enemy.getTeam() == rc.getTeam() && !firstMiner) {
                                    firstMiner = true;
                                    ignoreMiner = turnCount;
                                } else if (firstMiner && turnCount > ignoreMiner + 25 && turnCount < ignoreMiner + 40)
                                    tmpLocation = enemy.getLocation();
                        }
                }
            if (tmpLocation != null) {
                destination = tmpLocation;
                goingToEnemy = true;
            } else
                droneInitExplore();
            state = State.DRONE_EXPLORE;
        }
        if (state == State.DRONE_EXPLORE) {
            Direction bestDir = droneMoveTowards(destination);
            if (bestDir == Direction.CENTER) {
                droneInitExplore();
            } else if (rc.canMove(bestDir))
                rc.move(bestDir);
            if (rc.getLocation().distanceSquaredTo(homeDeliveryDrone) < 100)
                for (Direction direction : directions) {
                    RobotInfo robot = rc.senseRobotAtLocation(rc.adjacentLocation(direction));
                    if (robot == null) continue;
                    if (!robot.getType().isBuilding())
                        if (robot.getTeam() != rc.getTeam() || (robot.getType() == RobotType.MINER && hamiltonianDistance(robot.getLocation(), homeDeliveryDrone) <= defenceRadius + 1) ||
                                (robot.getType() == RobotType.LANDSCAPER && hamiltonianDistance(robot.getLocation(), homeDeliveryDrone) < defenceRadius))
                            if (robot.getType() == RobotType.MINER && robot.getTeam() == rc.getTeam() && !firstMiner) {
                                firstMiner = true;
                                ignoreMiner = turnCount;
                            } else if (firstMiner && turnCount > ignoreMiner + 25 && turnCount < ignoreMiner + 40)
                                if (rc.canPickUpUnit(robot.getID())) {
                                    rc.pickUpUnit(robot.getID());
                                    baseDeliveryDrone = rc.getLocation();
                                    randomDestination();
                                    if (robot.getTeam() != rc.getTeam())
                                        state = State.DROP_ENEMY;
                                    else if (robot.getType() == RobotType.MINER)
                                        state = State.DROP_FRIEND;
                                    else if (robot.getType() == RobotType.LANDSCAPER)
                                        state = State.DROP_DEFENDER;
                                }
                }
        }
        if (state == State.DROP_ENEMY) {
            for (Direction direction : directions) {
                MapLocation loc = rc.adjacentLocation(direction);
                if (rc.canDropUnit(direction) &&
                        (rc.getLocation().distanceSquaredTo(baseDeliveryDrone) > 100
                                || (rc.canSenseLocation(loc) &&
                                (rc.senseFlooding(loc) || (Math.abs(rc.senseElevation(loc) - homeElevation) > 5 && rc.getLocation().distanceSquaredTo(homeDeliveryDrone) > 70))))) {
                    rc.dropUnit(direction);
                    droneInitExplore();
                    state = State.DRONE_EXPLORE;
                    break;
                }
            }

            Direction dir = droneMoveTowards(destination);
            if (dir == Direction.CENTER)
                randomDestination();
            else if (rc.canMove(dir))
                rc.move(dir);
        }
        if (state == State.DROP_FRIEND) {
            if (rc.getLocation().distanceSquaredTo(baseDeliveryDrone) > 100) {
                for (Direction direction : directions) {
                    MapLocation loc = rc.adjacentLocation(direction);
                    boolean okelevation = Math.abs(rc.senseElevation(loc) - homeElevation) < 5;
                    if (rc.canSenseLocation(loc) && rc.senseSoup(loc) > 0)
                        okelevation = true;
                    if (rc.canSenseLocation(loc) && rc.senseElevation(loc) > homeElevation && turnCount > 500 && homeDeliveryDrone.distanceSquaredTo(loc) > 100)
                        okelevation = true;
                    if (rc.canDropUnit(direction) && rc.canSenseLocation(loc) && okelevation && !rc.senseFlooding(loc)) {
                        rc.dropUnit(direction);
                        droneInitExplore();
                        state = State.DRONE_EXPLORE;
                        break;
                    }
                }
            }

            Direction dir = droneMoveTowards(destination);
            if (dir == Direction.CENTER)
                randomDestination();
            else if (rc.canMove(dir))
                rc.move(dir);
        }
        if (state == State.DROP_DEFENDER) {
            MapLocation bestLocation = null;
            for (int i = 0; i < defendingRobotsNumber; i++) {
                MapLocation location = defencePositionsDrone[i];
                if (i >= defenceRadius * 4 && bestLocation != null)
                    break;
                if ((bestLocation == null || rc.getLocation().distanceSquaredTo(location) < rc.getLocation().distanceSquaredTo(bestLocation)) && rc.canSenseLocation(location) && rc.senseRobotAtLocation(location) == null)
                    bestLocation = location;
            }
            if (bestLocation != null) {
                if (bestLocation.isAdjacentTo(rc.getLocation()) && rc.canDropUnit(rc.getLocation().directionTo(bestLocation))) {
                    rc.dropUnit(rc.getLocation().directionTo(bestLocation));
                    state = State.DRONE_EXPLORE;
                    droneInitExplore();
                    return;
                }
                destination = bestLocation;
                Direction dir = droneMoveTowards(destination);
                if (dir == Direction.CENTER)
                    randomDestination();
                else if (rc.canMove(dir))
                    rc.move(dir);
            } else {
                for (Direction direction : directions) {
                    MapLocation loc = rc.adjacentLocation(direction);
                    if (loc.distanceSquaredTo(homeDeliveryDrone) > 100 && rc.canSenseLocation(loc) && !rc.senseFlooding(loc) && rc.canDropUnit(direction)) {
                        rc.dropUnit(direction);
                        state = State.DRONE_EXPLORE;
                        droneInitExplore();
                        return;
                    }
                }
                randomDestination();
                Direction dir = droneMoveTowards(destination);
                if (dir == Direction.CENTER)
                    randomDestination();
                else if (rc.canMove(dir))
                    rc.move(dir);
            }
        }
    }

    private static void findEnemyHQ() throws GameActionException {
        if (enemyHQ != null)
            return;
        for (int i = Math.max(1, rc.getRoundNum() - 800); i < rc.getRoundNum(); i++) {
            for (Transaction transaction : rc.getBlock(i))
                if (transaction.getMessage()[2] == rc.getTeam().ordinal() && transaction.getMessage()[3] == i + 1999 && transaction.getMessage()[4] == 1999) {
                    enemyHQ = new MapLocation(transaction.getMessage()[0], transaction.getMessage()[1]);
                    break;
                }
        }
        if (enemyHQ == null)
            for (RobotInfo robot : robots)
                if (robot.getType() == RobotType.HQ && robot.getTeam() != rc.getTeam()) {
                    enemyHQ = robot.getLocation();
                    int[] msg = new int[7];
                    msg[0] = enemyHQ.x;
                    msg[1] = enemyHQ.y;
                    msg[2] = rc.getTeam().ordinal();
                    msg[3] = rc.getRoundNum() + 1999;
                    msg[4] = 1999;
                    if (rc.canSubmitTransaction(msg, Math.min(70, rc.getTeamSoup())))
                        rc.submitTransaction(msg, Math.min(70, rc.getTeamSoup()));
                    return;
                }
    }

    static void droneInitExplore() {
        MapLocation loc = homeDeliveryDrone;
        int distance = rand.nextInt(3) + defenceRadius + 2,
                minX = Math.max(0, loc.x - distance),
                maxX = Math.min(rc.getMapWidth() - 1, loc.x + distance),
                x = rand.nextInt(maxX - minX + 1) + minX,
                z = rand.nextInt(2) * 2 - 1,
                y = loc.y + z * (distance - Math.abs(x - loc.x));

        MapLocation ret = new MapLocation(x, y);
        if (!isOnMap(ret)) ret = new MapLocation(x, 2 * loc.y - ret.y);
        destination = ret;
    }

    static void randomDestination() {
        destination = new MapLocation(rand.nextInt(rc.getMapWidth()), rand.nextInt(rc.getMapHeight()));
    }

    static void runNetGun() throws GameActionException {
        for (RobotInfo robot : robots)
            if (robot.getTeam() != rc.getTeam() && robot.getType() == RobotType.DELIVERY_DRONE)
                if (rc.canShootUnit(robot.getID()))
                    rc.shootUnit(robot.getID());
    }
}
